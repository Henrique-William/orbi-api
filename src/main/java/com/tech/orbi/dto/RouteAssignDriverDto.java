package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteAssignDriverDto(
        String driverId
) {}
