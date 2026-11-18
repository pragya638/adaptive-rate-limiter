package com.example.ratelimiter.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.history.DashboardHistoryDto;
import com.example.ratelimiter.history.DashboardHistoryService;

@RestController
@RequestMapping(path = "/api/history", produces = MediaType.APPLICATION_JSON_VALUE)
public class DashboardHistoryController {

    private final DashboardHistoryService historyService;

    public DashboardHistoryController(DashboardHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public DashboardHistoryDto history() {
        return historyService.snapshot();
    }
}
