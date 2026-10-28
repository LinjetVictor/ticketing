package com.example.ticketing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @Column(nullable = false)
    private Long eventId;

    @Column(nullable = false, unique = true, length = 36)
    private String holdId;

    @Column(nullable = false, length = 200)
    private String customerName;

    @Column(nullable = false, length = 320)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    private long totalCents;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingSeat> seats = new ArrayList<>();

    protected Booking() { }

    public Booking(String reference, Long eventId, String holdId, String customerName,
                   String customerEmail, LocalDateTime createdAt) {
        this.reference = reference;
        this.eventId = eventId;
        this.holdId = holdId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.createdAt = createdAt;
        this.status = BookingStatus.CONFIRMED;
    }

    public void addSeat(BookingSeat seat) {
        seat.setBooking(this);
        seats.add(seat);
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getEventId() { return eventId; }
    public String getHoldId() { return holdId; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }
    public List<BookingSeat> getSeats() { return seats; }
}
