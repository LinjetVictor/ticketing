package com.example.ticketing.service;

import com.example.ticketing.domain.Event;

public record EventSummary(Event event, long total, long available) {
    public boolean soldOut() { return total > 0 && available == 0; }
}
