package com.example.ticketing.web;

import com.example.ticketing.config.TicketingProperties;
import com.example.ticketing.exception.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final TicketingProperties props;

    public GlobalControllerAdvice(TicketingProperties props) {
        this.props = props;
    }

    @ModelAttribute("currency")
    public String currency() {
        return props.currency();
    }

    @ExceptionHandler(NotFoundException.class)
    public String notFound(NotFoundException e, Model model, HttpServletResponse response) {
        return render(404, "Not found", e.getMessage(), model, response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public String badRequest(BusinessRuleException e, Model model, HttpServletResponse response) {
        return render(400, "That didn't work", e.getMessage(), model, response);
    }

    @ExceptionHandler(HoldExpiredException.class)
    public String expired(HoldExpiredException e, Model model, HttpServletResponse response) {
        return render(410, "Hold expired", e.getMessage(), model, response);
    }

    @ExceptionHandler(SeatUnavailableException.class)
    public String conflict(SeatUnavailableException e, Model model, HttpServletResponse response) {
        return render(409, "Seats unavailable", e.getMessage(), model, response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String optimistic(ObjectOptimisticLockingFailureException e, Model model, HttpServletResponse response) {
        return render(409, "Someone else changed this first",
                "This record was updated by another user while you were working. Reload the page and try again.",
                model, response);
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public String redisDown(RedisConnectionFailureException e, Model model, HttpServletResponse response) {
        log.error("Redis unavailable", e);
        return render(503, "Seat reservations are temporarily unavailable",
                "We can't hold seats right now. Nothing was booked. Try again in a moment.", model, response);
    }

    @ExceptionHandler(Exception.class)
    public String unexpected(Exception e, Model model, HttpServletResponse response) {
        log.error("Unhandled error", e);
        return render(500, "Something went wrong", "An unexpected error occurred. Please try again.", model, response);
    }

    private String render(int status, String title, String message, Model model, HttpServletResponse response) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        return "error";
    }
}
