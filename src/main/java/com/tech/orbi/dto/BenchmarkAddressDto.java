package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkAddressDto(
        Integer id,
        String name,
        Double latitude,
        Double longitude,
        String address,
        String formattedAddress,
        Double characteristicSurfaceFactor,
        Boolean cachedFromGoogle
) {
    public double surfaceFactor() {
        return (characteristicSurfaceFactor != null && characteristicSurfaceFactor > 0)
                ? characteristicSurfaceFactor
                : 1.0;
    }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (address != null && !address.isBlank()) return address;
        return "Ponto " + (id != null ? id : "?");
    }
}
