package com.tech.orbi.dto;

import java.time.LocalDateTime;
import java.util.List;

public record BenchmarkBatchSummaryDto(
        String batchId,
        LocalDateTime createdAt,
        Integer totalScenarios,
        Double overallMaxSavingsPct,
        String overallBestAlgorithm,
        List<BenchmarkSessionSummaryDto> scenarioSessions
) {}
