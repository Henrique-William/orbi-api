package com.tech.orbi.dto;

import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public record RouteResponseDto(
        Long id,
        Driver driverId,
        User generatedById,
        String driverName,
        LocalDateTime createdAt, // Novo campo
        List<DeliveryDto> deliveries
) {}