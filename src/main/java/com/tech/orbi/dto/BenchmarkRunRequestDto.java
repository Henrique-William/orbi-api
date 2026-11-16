package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkRunRequestDto(
        String scenarioName,
        String scenarioDescription,
        List<BenchmarkAddressDto> addresses,
        String addressesFilePath,
        Integer stopsCount,
        Double dwellTimePerStopMin,
        Boolean includeExactMethods,
        Boolean saveToDatabase,
        Boolean runAllScenarios,
        String batchId,
        String scenarioCode
) {
    public BenchmarkRunRequestDto(
            String scenarioName,
            String scenarioDescription,
            List<BenchmarkAddressDto> addresses,
            String addressesFilePath,
            Integer stopsCount,
            Double dwellTimePerStopMin,
            Boolean includeExactMethods,
            Boolean saveToDatabase
    ) {
        this(scenarioName, scenarioDescription, addresses, addressesFilePath, stopsCount,
                dwellTimePerStopMin, includeExactMethods, saveToDatabase, false, null, null);
    }
}
