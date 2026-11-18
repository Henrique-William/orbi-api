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
        Boolean isValid,
        String status,
        String invalidReason,
        Double executionTimeMs,
        Double totalDistanceKm,
        Double totalDurationMin,
        Double totalCost,
        Double avgSurfaceFactor,
        Double efficiencyGainPct,
        Double optimalityGapPct,
        Double initialDurationMin,
        Double initialDistanceKm,
        String tourOrderJson,
        String routeCoordinatesJson,
        String notes,
        LocalDateTime createdAt
) {
    public BenchmarkResultDto(
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
            Double optimalityGapPct,
            Double initialDurationMin,
            Double initialDistanceKm,
            String tourOrderJson,
            String routeCoordinatesJson,
            String notes,
            LocalDateTime createdAt
    ) {
        this(id, sessionId, scenarioName, scenarioDescription, stopsCount,
                algorithmName, algorithmType, complexity,
                totalDistanceKm != null && totalDistanceKm > 0,
                (totalDistanceKm != null && totalDistanceKm > 0) ? "VALID" : "INVALID",
                null,
                executionTimeMs, totalDistanceKm, totalDurationMin, totalCost,
                avgSurfaceFactor, efficiencyGainPct, optimalityGapPct,
                initialDurationMin, initialDistanceKm, tourOrderJson,
                routeCoordinatesJson, notes, createdAt);
    }

    public BenchmarkResultDto(
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
    ) {
        this(id, sessionId, scenarioName, scenarioDescription, stopsCount,
                algorithmName, algorithmType, complexity,
                totalDistanceKm != null && totalDistanceKm > 0,
                (totalDistanceKm != null && totalDistanceKm > 0) ? "VALID" : "INVALID",
                null,
                executionTimeMs, totalDistanceKm, totalDurationMin, totalCost,
                avgSurfaceFactor, efficiencyGainPct, null,
                initialDurationMin, initialDistanceKm, tourOrderJson,
                routeCoordinatesJson, notes, createdAt);
    }
}
