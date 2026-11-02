package com.adityakumar.urlshortener.repository;

import com.adityakumar.urlshortener.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    /** Rows of [date, count] for one link since the given time, oldest first. */
    @Query("""
            select cast(c.clickedAt as date), count(c)
            from ClickEvent c
            where c.link.id = :linkId and c.clickedAt >= :since
            group by cast(c.clickedAt as date)
            order by cast(c.clickedAt as date)
            """)
    List<Object[]> countByDay(@Param("linkId") Long linkId, @Param("since") LocalDateTime since);
}
