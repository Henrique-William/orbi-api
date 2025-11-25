package com.tech.orbi.dto;

import java.util.UUID;

public record LocationDto(
        String address,
        double latitude,
        double longitude,
        String recipientName,
        String recipientPhone,
        String recipientEmail,
        String packageDetails,
        UUID driverId
) {
}