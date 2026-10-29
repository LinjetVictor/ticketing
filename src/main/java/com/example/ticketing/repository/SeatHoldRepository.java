package com.example.ticketing.repository;

import com.example.ticketing.domain.HoldStatus;
import com.example.ticketing.domain.SeatHold;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, String> {

    /** Row lock on the hold: concurrent confirms / releases of the same hold are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from SeatHold h where h.id = :id")
    Optional<SeatHold> findByIdForUpdate(@Param("id") String id);

    /**
     * Reaper query: FOR UPDATE SKIP LOCKED (-2 == LockOptions.SKIP_LOCKED). Rows a concurrent confirm
     * (or another app instance's reaper) is working on are skipped instead of waited for.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select h from SeatHold h where h.status = :status and h.expiresAt < :cutoff order by h.expiresAt")
    List<SeatHold> lockExpired(@Param("status") HoldStatus status,
                               @Param("cutoff") LocalDateTime cutoff,
                               Pageable page);
}
