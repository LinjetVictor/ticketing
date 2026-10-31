package com.example.ticketing.service;

import com.example.ticketing.domain.Event;
import com.example.ticketing.domain.HoldStatus;
import com.example.ticketing.domain.Seat;

import java.util.List;

public record HoldDetails(String holdId, HoldStatus status, Event event, List<Seat> seats,
                          long totalCents, long secondsRemaining, boolean active) { }
