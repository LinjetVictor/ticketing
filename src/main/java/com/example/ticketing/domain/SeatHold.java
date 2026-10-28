package com.example.ticketing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Table(name = "seat_hold")
public class SeatHold {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HoldStatus status;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seat_hold_seat", joinColumns = @JoinColumn(name = "hold_id"))
    @Column(name = "seat_id", nullable = false)
    private Set<Long> seatIds = new TreeSet<>();

    @Version
    private Long version;

    protected SeatHold() { }

    public SeatHold(String id, Long eventId, Collection<Long> seatIds, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.id = id;
        this.eventId = eventId;
        this.seatIds = new TreeSet<>(seatIds);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = HoldStatus.ACTIVE;
    }

    public String getId() { return id; }
    public Long getEventId() { return eventId; }
    public HoldStatus getStatus() { return status; }
    public void setStatus(HoldStatus status) { this.status = status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Set<Long> getSeatIds() { return seatIds; }
    public Long getVersion() { return version; }
}
