package com.example.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AppConfig {

    /** Single time source for the whole app, so time-dependent logic stays testable. */
    @Bean
    public Clock clock(TicketingProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }
}
