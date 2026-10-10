package com.tech.orbi.dto;

import java.util.List;
import java.util.Map;

public record RouteDirectionsDto(
        Integer routeId,
        double distanceMeters,
        double durationSeconds,
        Geometry geometry,
        List<Segment> segments,
        Map<String, ExtraInfo> extras
) {
    public record Point(double latitude, double longitude) {
    }

    public record Geometry(String type, List<List<Double>> coordinates) {
    }

    public record Segment(
            double distanceMeters,
            double durationSeconds,
            List<Step> steps
    ) {
    }

    public record Step(
            String name,
            String instruction,
            double distanceMeters,
            double durationSeconds,
            int type,
            List<Integer> wayPoints
    ) {
    }

    public record ExtraInfo(
            List<List<Integer>> values,
            List<ExtraSummary> summary
    ) {
    }

    public record ExtraSummary(
            int value,
            double distanceMeters,
            double amount
    ) {
    }
}
