package com.example.ratelimiter.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ratelimiter.adaptive.AdaptiveStatusDto;
import com.example.ratelimiter.adaptive.AdaptiveStatusService;

@RestController
@RequestMapping(path = "/api/adaptive", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdaptiveStatusController {

    private final AdaptiveStatusService statusService;

    public AdaptiveStatusController(AdaptiveStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    public AdaptiveStatusDto status() {
        return statusService.snapshot();
    }
}
