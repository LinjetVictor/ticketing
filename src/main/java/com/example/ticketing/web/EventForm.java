package com.example.ticketing.web;

import com.example.ticketing.domain.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class EventForm {

    private Long id;
    private Long version;

    @NotBlank(message = "Enter an event name")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "Enter a venue")
    @Size(max = 200)
    private String venue;

    @Size(max = 2000)
    private String description;

    @NotNull(message = "Choose a start date and time")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startsAt;

    private EventStatus status = EventStatus.ON_SALE;

    // Seat-grid fields: used on create only.
    private String section = "Main";
    private Integer rows = 8;
    private Integer seatsPerRow = 12;
    private BigDecimal price = new BigDecimal("50.00");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
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
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public Integer getRows() { return rows; }
    public void setRows(Integer rows) { this.rows = rows; }
    public Integer getSeatsPerRow() { return seatsPerRow; }
    public void setSeatsPerRow(Integer seatsPerRow) { this.seatsPerRow = seatsPerRow; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
