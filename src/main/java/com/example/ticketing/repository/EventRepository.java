package com.example.ticketing.repository;

import com.example.ticketing.domain.Event;
import com.example.ticketing.domain.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderByStartsAtAsc();

    List<Event> findByStatusAndStartsAtAfterOrderByStartsAtAsc(EventStatus status, LocalDateTime after);

    /** Bulk update bypasses the persistence context, so the @Version column is bumped by hand. */
    @Modifying
    @Query("update Event e set e.status = :closed, e.version = e.version + 1 "
            + "where e.status = :onSale and e.startsAt <= :now")
    int closeStarted(@Param("now") LocalDateTime now,
                     @Param("onSale") EventStatus onSale,
                     @Param("closed") EventStatus closed);
}
