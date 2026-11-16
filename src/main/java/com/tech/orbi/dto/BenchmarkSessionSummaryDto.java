package com.tech.orbi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BenchmarkSessionSummaryDto(
        String sessionId,
        String batchId,
        String scenarioCode,
        String scenarioName,
        String scenarioDescription,
        Integer stopsCount,
        Double initialDurationMin,
        Double initialDistanceKm,
        Double bestDistanceKm,
        Double bestDurationMin,
        String bestAlgorithm,
        Double maxSavingsPct,
        LocalDateTime createdAt,
        List<BenchmarkResultDto> models
) {
    public BenchmarkSessionSummaryDto(
            String sessionId,
            String scenarioName,
            String scenarioDescription,
            Integer stopsCount,
            Double initialDurationMin,
            Double initialDistanceKm,
            Double bestDistanceKm,
            Double bestDurationMin,
            String bestAlgorithm,
            Double maxSavingsPct,
            LocalDateTime createdAt,
            List<BenchmarkResultDto> models
    ) {
        this(sessionId, null, null, scenarioName, scenarioDescription, stopsCount,
                initialDurationMin, initialDistanceKm, bestDistanceKm, bestDurationMin,
                bestAlgorithm, maxSavingsPct, createdAt, models);
    }
}
