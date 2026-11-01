package com.example.ticketing.web;

import com.example.ticketing.domain.Booking;
import com.example.ticketing.domain.BookingStatus;
import com.example.ticketing.domain.Event;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.exception.HoldExpiredException;
import com.example.ticketing.exception.SeatUnavailableException;
import com.example.ticketing.service.BookingService;
import com.example.ticketing.service.CancellationService;
import com.example.ticketing.service.EventService;
import com.example.ticketing.service.HoldDetails;
import com.example.ticketing.service.HoldService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;

@Controller
public class CheckoutController {

    private final HoldService holdService;
    private final BookingService bookingService;
    private final CancellationService cancellationService;
    private final EventService eventService;
    private final Clock clock;

    public CheckoutController(HoldService holdService, BookingService bookingService,
                              CancellationService cancellationService, EventService eventService, Clock clock) {
        this.holdService = holdService;
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
        this.eventService = eventService;
        this.clock = clock;
    }

    @GetMapping("/holds/{id}")
    public String checkout(@PathVariable String id, Model model) {
        model.addAttribute("details", holdService.describe(id));
        if (!model.containsAttribute("confirmForm")) {
            model.addAttribute("confirmForm", new ConfirmForm());
        }
        return "checkout";
    }

    @PostMapping("/holds/{id}/confirm")
    public String confirm(@PathVariable String id,
                          @Valid @ModelAttribute("confirmForm") ConfirmForm form,
                          BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("details", holdService.describe(id));
            return "checkout";
        }
        try {
            Booking booking = bookingService.confirm(id, form.getName(), form.getEmail());
            return "redirect:/bookings/" + booking.getReference();
        } catch (HoldExpiredException | SeatUnavailableException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/holds/" + id;
        }
    }

    @PostMapping("/holds/{id}/release")
    public String release(@PathVariable String id, RedirectAttributes redirect) {
        HoldDetails details = holdService.describe(id);
        holdService.release(id);
        redirect.addFlashAttribute("success", "Seats released.");
        return "redirect:/events/" + details.event().getId();
    }

    @GetMapping("/bookings/{reference}")
    public String booking(@PathVariable String reference, Model model) {
        Booking booking = bookingService.getByReference(reference);
        Event event = eventService.get(booking.getEventId());
        model.addAttribute("booking", booking);
        model.addAttribute("event", event);
        model.addAttribute("canCancel", booking.getStatus() == BookingStatus.CONFIRMED
                && event.getStartsAt().isAfter(LocalDateTime.now(clock)));
        return "booking";
    }

    @PostMapping("/bookings/{reference}/cancel")
    public String cancel(@PathVariable String reference, RedirectAttributes redirect) {
        try {
            cancellationService.cancel(reference);
            redirect.addFlashAttribute("success", "Booking cancelled. The seats are available again.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/bookings/" + reference;
    }
}
