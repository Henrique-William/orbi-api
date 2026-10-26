package com.tech.orbi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationDto(
        String address,
        double latitude,
        double longitude,
        String recipientEmail,
        String recipientName,
        String recipientPhone,
        String packageDetails
) {
    public LocationDto(String address, double latitude, double longitude) {
        this(address, latitude, longitude, null, null, null, null);
    }

    public LocationDto(String address, double latitude, double longitude, UUID driverId) {
        this(address, latitude, longitude, null, null, null, null);
    }
}
