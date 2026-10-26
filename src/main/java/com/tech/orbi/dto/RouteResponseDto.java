package com.tech.orbi.dto;

import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.RouteStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RouteResponseDto(
        Long id,
        Driver driverId,
        UUID createdBy,
        String driverName,
        RouteStatus status,
        double totalDistance,
        double totalCost,
        Integer totalStops,
        LocalDateTime createdAt,
        LocalDateTime assignedAt,
        LocalDateTime finalizedAt
) {}
