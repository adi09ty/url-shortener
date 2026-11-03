# URL Shortener

A Spring Boot service that turns long URLs into short links and redirects visitors to them. It supports optional link expiry, per-day click analytics, Redis caching of hot lookups, and per-client rate limiting.

## Features
- Create a short link for any `http(s)` URL, with an optional custom alias and optional expiry (in days)
- `GET /{code}` redirects with `302`; unknown codes return `404`, expired links return `410 Gone`
- Click analytics: total clicks, last click time and clicks per day (days with no clicks are included as zero)
- Cache-aside caching in Redis for redirect lookups; cache lifetime never exceeds a link's remaining life
- Fixed-window rate limiting per client IP, with separate limits for creating links and for redirects (`429` + `Retry-After`)
- Input validation, RFC 7807 error responses, health endpoint
- Redis problems never break redirects: cache and rate limiter fail open, a small circuit breaker skips Redis for 10 seconds after a failure, and redirects fall back to the database
- Runs with zero setup (in-memory H2) or as a MySQL + Redis stack with Docker Compose

## Tech stack
Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA / Hibernate, MySQL 8.4, Redis 7 (Spring Data Redis), Spring Boot Actuator, JUnit 5, Mockito, MockMvc, Maven, Docker.

## Architecture
```
Client
  |
RateLimitInterceptor ---- RateLimiter (Redis | in-memory)
  |
Controllers (LinkController, RedirectController)
  |
LinkService ---- LinkCache (Redis | in-memory)
  |
Repositories ---- MySQL (H2 for local runs)
```
- `web`: REST controllers and the global exception handler
- `service`: link creation, URL validation, redirect resolution, analytics
- `cache` and `ratelimit`: small interfaces with a Redis and an in-memory implementation each, chosen by `app.redis.enabled`
- `repository` and `domain`: JPA entities `Link` and `ClickEvent` and their Spring Data repositories
- `dto`: request and response records (entities are never exposed directly)

### What happens on `GET /{code}`
1. The rate limiter counts the request for the client's IP (limit exceeded: `429`).
2. The cache is asked for the code. On a miss, the link is loaded from MySQL and cached.
3. If the link has expired: `410 Gone`. If it does not exist: `404`.
4. The click is recorded (an atomic `UPDATE` of the counter plus a `click_events` row).
5. The response is `302 Found` with a `Location` header.

## Run it

### Quick start (no database needed)
Requires Java 21+.
```bash
./mvnw spring-boot:run
```
On Windows use `.\mvnw.cmd spring-boot:run`. This uses an in-memory H2 database, an in-memory cache and an in-memory rate limiter. The app is at http://localhost:8080.

### Docker Compose (MySQL + Redis)
1. Create your environment file and set your own values:
   ```bash
   cp .env.example .env
   ```
2. Start the stack:
   ```bash
   docker compose up --build
   ```
MySQL is exposed on `127.0.0.1:3308` and Redis on `127.0.0.1:6380` for inspection with tools such as MySQL Workbench or `redis-cli`.

To run the app yourself against your own MySQL and Redis, use the `mysql` profile and set `DB_URL`, `DB_USER`, `DB_PASSWORD` and optionally `REDIS_HOST`, `REDIS_PORT`, `APP_BASE_URL`.

## Configuration
| Property | Default | Meaning |
|---|---|---|
| `app.base-url` | `http://localhost:8080` | Used to build the `shortUrl` in responses |
| `app.redis.enabled` | `false` (`true` in the `mysql` profile) | Use Redis for cache and rate limiting |
| `app.cache.ttl` | `1h` | Maximum cache lifetime of a link |
| `app.rate-limit.create-per-window` | `20` | Link creations per client per window |
| `app.rate-limit.redirect-per-window` | `120` | Redirects per client per window |
| `app.rate-limit.window` | `1m` | Rate limit window length |

## API
### Create a link
```bash
curl -i -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.example.com/some/very/long/path?x=1","alias":"my-link","expiresInDays":7}'
```
`alias` and `expiresInDays` are optional. Response `201 Created`:
```json
{
  "shortCode": "my-link",
  "shortUrl": "http://localhost:8080/my-link",
  "originalUrl": "https://www.example.com/some/very/long/path?x=1",
  "createdAt": "2026-03-10T08:00:00",
  "expiresAt": "2026-03-17T08:00:00",
  "expired": false,
  "clickCount": 0,
  "lastClickedAt": null
}
```
### Follow a link
```bash
curl -i http://localhost:8080/my-link
```
`302 Found` with `Location: https://www.example.com/some/very/long/path?x=1`.

### Link details
```bash
curl http://localhost:8080/api/links/my-link
```
### Analytics
```bash
curl "http://localhost:8080/api/links/my-link/analytics?days=7"
```
```json
{
  "shortCode": "my-link",
  "totalClicks": 3,
  "days": 7,
  "clicksByDay": [
    { "date": "2026-03-04", "clicks": 0 },
    { "date": "2026-03-10", "clicks": 3 }
  ]
}
```
(`clicksByDay` has one entry for each of the `days` days; the example is shortened.)

### Status codes
| Code | When |
|---|---|
| `201` | Link created |
| `302` | Redirect |
| `400` | Invalid URL, alias, expiry, parameter or malformed JSON |
| `404` | Unknown short code |
| `409` | Alias already taken or reserved |
| `410` | Link has expired |
| `429` | Rate limit exceeded (see `Retry-After`) |

Health check: `GET /actuator/health`.

## Tests
```bash
./mvnw test
```
55 tests:
- **Unit tests:** URL validation (rejects `javascript:`, `ftp:`, `file:` and malformed input), code generation, cache entry encoding, the rate limiter (including window rollover with a controllable clock), and `LinkService` with mocks (cache hit skips the database, cache lifetime capped by expiry, expired links are not cached or counted, alias rules).
- **API tests (MockMvc, H2):** create, redirect, click counting, caching, aliases, validation errors, expiry via a movable clock, analytics, rate limiting and health.

The Redis implementations are unit-tested against a failing Redis (fail-open and circuit-breaker behaviour) and were exercised against a real Redis through the Docker Compose stack.

## Design decisions
- **302, not 301.** Browsers cache a 301 and stop contacting the server, which would stop click counting and make expiry unenforceable.
- **Random codes, not sequential IDs.** 8 characters from `a-z0-9` (about 2.8 trillion combinations) cannot be guessed or enumerated. Codes are lowercase and lookups are case-insensitive, so behaviour is identical on case-insensitive MySQL collations.
- **Cache-aside.** The database stays the source of truth; a cache miss or a Redis outage is never an error.
- **Atomic click counting.** `UPDATE ... SET click_count = click_count + 1` avoids lost updates under concurrent redirects.
- **Atomic rate limiting.** The Redis counter uses a small Lua script (`INCR` + `PEXPIRE`) so a counter can never be left without an expiry.
- **Only `http`/`https` URLs** are accepted.
- **Client identity for rate limiting is the socket address.** `X-Forwarded-For` is client-controlled and would let anyone bypass the limit; behind a trusted proxy, configure forwarded-header handling in the server.

## Known limitations
- Rate limiting uses fixed windows, so a client can send up to twice the limit around a window boundary.
- Every redirect writes to the database (counter and click event), so write volume grows with traffic; a production system would batch or queue this.
- There is no authentication: anyone can create links and read the analytics of any code.
- The schema is created with `ddl-auto=update`; a production deployment should use a migration tool such as Flyway.
- Timestamps are stored in UTC without a zone.
