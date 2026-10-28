package com.example.ticketing.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 50)
    private String section;

    @Column(nullable = false, length = 10)
    private String rowLabel;

    @Column(nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private long priceCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    /** Second line of defence: any write based on a stale read fails with an optimistic-lock error. */
    @Version
    private Long version;

    protected Seat() { }

    public Seat(Long eventId, String section, String rowLabel, int seatNumber, long priceCents) {
        this.eventId = eventId;
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.priceCents = priceCents;
        this.status = SeatStatus.AVAILABLE;
    }

    public String getLabel() { return rowLabel + seatNumber; }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getSection() { return section; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public long getPriceCents() { return priceCents; }
    public SeatStatus getStatus() { return status; }
    public void setStatus(SeatStatus status) { this.status = status; }
    public Long getVersion() { return version; }
}
