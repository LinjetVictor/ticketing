package com.example.ticketing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Optimistic lock: concurrent admin edits of the same event are detected, not silently merged. */
    @Version
    private Long version;

    protected Event() { }

    public Event(String name, String venue, String description, LocalDateTime startsAt, LocalDateTime createdAt) {
        this.name = name;
        this.venue = venue;
        this.description = description;
        this.startsAt = startsAt;
        this.createdAt = createdAt;
        this.status = EventStatus.ON_SALE;
    }

    public boolean isOnSale(LocalDateTime now) {
        return status == EventStatus.ON_SALE && startsAt.isAfter(now);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
}
