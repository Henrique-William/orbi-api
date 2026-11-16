package com.tech.orbi.dto;

import java.time.LocalDateTime;

public record BenchmarkResultDto(
        Long id,
        String sessionId,
        String scenarioName,
        String scenarioDescription,
        Integer stopsCount,
        String algorithmName,
        String algorithmType,
        String complexity,
        Double executionTimeMs,
        Double totalDistanceKm,
        Double totalDurationMin,
        Double totalCost,
        Double avgSurfaceFactor,
        Double efficiencyGainPct,
        Double initialDurationMin,
        Double initialDistanceKm,
        String tourOrderJson,
        String routeCoordinatesJson,
        String notes,
        LocalDateTime createdAt
) {}
