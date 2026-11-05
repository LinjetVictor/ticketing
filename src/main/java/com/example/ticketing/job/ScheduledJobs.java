package com.example.ticketing.job;

import com.example.ticketing.config.TicketingProperties;
import com.example.ticketing.redis.DistributedJobLock;
import com.example.ticketing.service.EventService;
import com.example.ticketing.service.HoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final HoldService holdService;
    private final EventService eventService;
    private final DistributedJobLock lock;
    private final TicketingProperties props;

    public ScheduledJobs(HoldService holdService, EventService eventService,
                         DistributedJobLock lock, TicketingProperties props) {
        this.holdService = holdService;
        this.eventService = eventService;
        this.lock = lock;
        this.props = props;
    }

    /** Reconciles hold rows whose Redis keys have already expired */
    @Scheduled(fixedDelayString = "${ticketing.jobs.hold-expiry-interval-ms:10000}")
    public void expireHolds() {
        run("hold-expiry", () -> {
            int n = holdService.expireStaleHolds();
            if (n > 0) {
                log.info("Marked {} stale hold(s) as EXPIRED", n);
            }
        });
    }

    /** Stops sales for events that have started */
    @Scheduled(cron = "0 * * * * *")
    public void closeStartedEvents() {
        run("event-close", () -> {
            int n = eventService.closeStartedEvents();
            if (n > 0) {
                log.info("Closed sales for {} event(s) that have started", n);
            }
        });
    }

    private void run(String name, Runnable task) {
        try {
            lock.runExclusively(name, props.jobs().lockTtl(), task);
        } catch (RuntimeException e) {
            log.error("Scheduled job '{}' failed: {}", name, e.toString(), e);
        }
    }
}
