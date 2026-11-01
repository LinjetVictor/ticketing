package com.example.ticketing.web;

import com.example.ticketing.config.TicketingProperties;
import com.example.ticketing.domain.Event;
import com.example.ticketing.domain.SeatHold;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.SeatUnavailableException;
import com.example.ticketing.service.EventService;
import com.example.ticketing.service.HoldService;
import com.example.ticketing.service.SeatMapService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class EventController {

    private final EventService eventService;
    private final SeatMapService seatMapService;
    private final HoldService holdService;
    private final TicketingProperties props;
    private final Clock clock;

    public EventController(EventService eventService, SeatMapService seatMapService, HoldService holdService,
                           TicketingProperties props, Clock clock) {
        this.eventService = eventService;
        this.seatMapService = seatMapService;
        this.holdService = holdService;
        this.props = props;
        this.clock = clock;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("events", eventService.upcomingSummaries());
        return "events";
    }

    @GetMapping("/events/{id}")
    public String event(@PathVariable long id, Model model) {
        Event event = eventService.get(id);
        model.addAttribute("event", event);
        model.addAttribute("seatMap", seatMapService.build(id));
        model.addAttribute("onSale", event.isOnSale(LocalDateTime.now(clock)));
        model.addAttribute("maxSeats", props.hold().maxSeats());
        model.addAttribute("holdLabel", describe(props.hold().ttl()));
        return "event";
    }

    @PostMapping("/events/{id}/holds")
    public String hold(@PathVariable long id,
                       @RequestParam(name = "seatIds", required = false) List<Long> seatIds,
                       RedirectAttributes redirect) {
        try {
            SeatHold hold = holdService.createHold(id, seatIds);
            return "redirect:/holds/" + hold.getId();
        } catch (SeatUnavailableException | BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/events/" + id;
        }
    }

    private static String describe(Duration d) {
        return d.toMinutes() >= 1 ? d.toMinutes() + " minutes" : d.toSeconds() + " seconds";
    }
}
