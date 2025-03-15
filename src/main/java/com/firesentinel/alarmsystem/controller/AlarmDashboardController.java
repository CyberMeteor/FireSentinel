package com.firesentinel.alarmsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving the Alarm Dashboard web interface.
 * This controller handles HTTP requests for the alarm monitoring dashboard.
 */
@Controller
@RequestMapping("/dashboard")
public class AlarmDashboardController {

    /**
     * Serves the main alarm dashboard page.
     * 
     * @return The name of the view to render (in this case, forwards to the static HTML)
     */
    @GetMapping
    public String getDashboard() {
        return "forward:/alarm-dashboard.html";
    }
} 