package com.example.ticketing.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "booking_seat")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private Long seatId;

    /** = seatId while active, null after cancellation. Unique in the DB => no double booking, ever. */
    @Column(unique = true)
    private Long activeSeatId;

    @Column(nullable = false, length = 50)
    private String section;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(nullable = false)
    private long priceCents;

    protected BookingSeat() { }

    public BookingSeat(Long seatId, String section, String label, long priceCents) {
        this.seatId = seatId;
        this.activeSeatId = seatId;
        this.section = section;
        this.label = label;
        this.priceCents = priceCents;
    }

    public void deactivate() { this.activeSeatId = null; }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    void setBooking(Booking booking) { this.booking = booking; }
    public Long getSeatId() { return seatId; }
    public Long getActiveSeatId() { return activeSeatId; }
    public String getSection() { return section; }
    public String getLabel() { return label; }
    public long getPriceCents() { return priceCents; }
}
