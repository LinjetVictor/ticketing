package com.example.ticketing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketing")
public record TicketingProperties(String currency, String zone, boolean seed, Hold hold, Jobs jobs) {

    public record Hold(Duration ttl, int maxSeats, Duration expiryGrace) { }

    public record Jobs(Duration lockTtl) { }
}
