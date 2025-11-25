package com.tech.orbi.controller;

import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/optimize")
    public ResponseEntity<List<LocationDto>> getBestRoute(
            @RequestBody List<LocationDto> locations,
            @RequestParam(defaultValue = "0") int startIndex) {

        if (locations == null || locations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, startIndex);

        return ResponseEntity.ok(optimizedRoute);
    }

}