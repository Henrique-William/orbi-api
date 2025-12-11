package com.tech.orbi.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RouteResponseDto(
        Integer id,
        UUID driverId,
        String driverName,
        LocalDateTime createdAt, // Novo campo
        List<DeliveryDto> deliveries
) {}