package com.example.ticketing.config;

import com.example.ticketing.repository.EventRepository;
import com.example.ticketing.service.CreateEventCommand;
import com.example.ticketing.service.EventService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "ticketing", name = "seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final EventRepository events;
    private final EventService eventService;
    private final Clock clock;

    public DemoDataSeeder(EventRepository events, EventService eventService, Clock clock) {
        this.events = events;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (events.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        eventService.create(new CreateEventCommand("Nairobi Jazz Night", "Kenya National Theatre",
                "An evening of live jazz in the heart of the city.",
                now.plusDays(14).withHour(19).withMinute(30).withSecond(0).withNano(0),
                "Stalls", 8, 12, 4500));
        eventService.create(new CreateEventCommand("Cloud Native Summit", "KICC Auditorium",
                "One day of talks on platforms, data and reliability.",
                now.plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0),
                "Hall A", 6, 16, 12000));
    }
}
