package com.tech.orbi.dto.ors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class OrsDtos {

    private OrsDtos() {
    }

    public record MatrixRequest(
            List<List<Double>> locations,
            List<String> metrics,
            String units,
            @JsonProperty("resolve_locations") boolean resolveLocations
    ) {
    }

    public record MatrixResponse(Double[][] distances) {
    }

    public record DirectionsRequest(
            List<List<Double>> coordinates,
            boolean instructions,
            String units,
            @JsonProperty("extra_info") List<String> extraInfo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DirectionsResponse(String type, List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Geometry geometry, Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(String type, List<List<Double>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            Summary summary,
            List<Segment> segments,
            Map<String, ExtraInfo> extras
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Double distance, Double duration) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(Double distance, Double duration, List<Step> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            String name,
            String instruction,
            Double distance,
            Double duration,
            Integer type,
            @JsonProperty("way_points") List<Integer> wayPoints
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtraInfo(List<List<Integer>> values, List<ExtraSummary> summary) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtraSummary(Integer value, Double distance, Double amount) {
    }
}
