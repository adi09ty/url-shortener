package com.adityakumar.urlshortener.ratelimit;

import com.adityakumar.urlshortener.config.AppProperties;
import com.adityakumar.urlshortener.exception.RateLimitExceededException;
import com.adityakumar.urlshortener.web.LinkController;
import com.adityakumar.urlshortener.web.RedirectController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits each client (by IP) separately for link creation and for redirects.
 * Creating links is expensive and abusable, so it has a much lower limit than redirects.
 *
 * Uses the socket's remote address, not X-Forwarded-For: that header is client-controlled and
 * would let anyone dodge the limit. Behind a trusted reverse proxy, configure the server's
 * forwarded-header support so getRemoteAddr() returns the real client.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter limiter;
    private final AppProperties.RateLimit settings;

    public RateLimitInterceptor(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.settings = properties.rateLimit();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        String bucket;
        int limit;
        if (method.getBeanType() == RedirectController.class) {
            bucket = "redirect";
            limit = settings.redirectPerWindow();
        } else if (method.getBeanType() == LinkController.class && "POST".equals(request.getMethod())) {
            bucket = "create";
            limit = settings.createPerWindow();
        } else {
            return true;
        }

        RateLimitDecision decision = limiter.check(bucket + ":" + request.getRemoteAddr(), limit, settings.window());
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        return true;
    }
}
