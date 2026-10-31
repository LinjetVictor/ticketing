package com.example.ticketing.service;

import java.time.LocalDateTime;

public record CreateEventCommand(String name, String venue, String description, LocalDateTime startsAt,
                                 String section, int rows, int seatsPerRow, long priceCents) { }
