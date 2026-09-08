package com.tech.orbi.dto;

import java.util.UUID;

public record LocationDto(
        String address,
        double latitude,
        double longitude,
        UUID driverId
) {
}