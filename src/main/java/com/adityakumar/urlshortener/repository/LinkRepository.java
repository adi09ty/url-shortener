package com.adityakumar.urlshortener.repository;

import com.adityakumar.urlshortener.domain.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /** Single UPDATE so concurrent clicks never lose a count (no read-modify-write in Java). */
    @Modifying
    @Query("update Link l set l.clickCount = l.clickCount + 1, l.lastClickedAt = :now where l.id = :id")
    int recordClick(@Param("id") Long id, @Param("now") LocalDateTime now);
}
