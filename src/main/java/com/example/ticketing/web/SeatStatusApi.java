package com.example.ticketing.web;

import com.example.ticketing.service.SeatMapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeatStatusApi {

    private final SeatMapService seatMapService;

    public SeatStatusApi(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping("/api/events/{id}/seats")
    public Map<Long, String> seats(@PathVariable long id) {
        return seatMapService.statuses(id);
    }
}
