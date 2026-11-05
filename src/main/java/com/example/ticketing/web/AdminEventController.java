package com.example.ticketing.web;

import com.example.ticketing.domain.Event;
import com.example.ticketing.domain.EventStatus;
import com.example.ticketing.exception.BusinessRuleException;
import com.example.ticketing.service.CreateEventCommand;
import com.example.ticketing.service.EventService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Controller
@RequestMapping("/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("events", eventService.allSummaries());
        return "admin/events";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("eventForm", new EventForm());
        model.addAttribute("creating", true);
        model.addAttribute("statuses", EventStatus.values());
        return "admin/event-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("eventForm") EventForm form, BindingResult result,
                         Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            if (form.getRows() == null || form.getSeatsPerRow() == null || form.getPrice() == null) {
                result.reject("grid", "Enter rows, seats per row and a ticket price");
            }
        }
        if (result.hasErrors()) {
            return formView(model, true);
        }
        try {
            long cents = toCents(form.getPrice());
            Event e = eventService.create(new CreateEventCommand(form.getName(), form.getVenue(),
                    form.getDescription(), form.getStartsAt(), form.getSection(),
                    form.getRows(), form.getSeatsPerRow(), cents));
            redirect.addFlashAttribute("success", "Created \"" + e.getName() + "\" with "
                    + (form.getRows() * form.getSeatsPerRow()) + " seats.");
            return "redirect:/admin/events";
        } catch (BusinessRuleException ex) {
            result.reject("rule", ex.getMessage());
            return formView(model, true);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        Event e = eventService.get(id);
        EventForm form = new EventForm();
        form.setId(e.getId());
        form.setVersion(e.getVersion());        // travels with the form: this is the optimistic-lock token
        form.setName(e.getName());
        form.setVenue(e.getVenue());
        form.setDescription(e.getDescription());
        form.setStartsAt(e.getStartsAt());
        form.setStatus(e.getStatus());
        model.addAttribute("eventForm", form);
        return formView(model, false);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("eventForm") EventForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return formView(model, false);
        }
        // An OptimisticLockingFailureException (stale version) is turned into a 409 page by GlobalControllerAdvice.
        eventService.update(id, form.getVersion(), form.getName(), form.getVenue(), form.getDescription(),
                form.getStartsAt(), form.getStatus());
        redirect.addFlashAttribute("success", "Event updated.");
        return "redirect:/admin/events";
    }

    private String formView(Model model, boolean creating) {
        model.addAttribute("creating", creating);
        model.addAttribute("statuses", EventStatus.values());
        return "admin/event-form";
    }

    private static long toCents(BigDecimal price) {
        return price.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
