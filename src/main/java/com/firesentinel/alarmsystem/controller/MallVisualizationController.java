package com.firesentinel.alarmsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for serving the Mall Visualization web interface.
 * This controller handles HTTP requests for the 3D mall visualization dashboard.
 */
@Controller
@RequestMapping("/visualization")
public class MallVisualizationController {

    /**
     * Serves the mall visualization page.
     * 
     * @return The name of the view to render (in this case, forwards to the static HTML)
     */
    @GetMapping
    public String getVisualization() {
        return "forward:/mall-visualization.html";
    }
} 