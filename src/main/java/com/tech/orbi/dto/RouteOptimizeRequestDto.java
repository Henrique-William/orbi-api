package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteOptimizeRequestDto(
        @JsonAlias({"assignedDriverId", "driverId", "assignedDriver"})
        String driverId,

        @JsonAlias({"locations", "stops"})
        List<LocationDto> locations
) {}
