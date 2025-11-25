package com.tech.orbi.dto;

import java.util.List;
import java.util.UUID;

public record RouteResponseDto(
        Integer id,
        UUID driverId,
        String driverName,
        List<DeliveryDto> deliveries // Alterado de List<Delivery> para List<DeliveryDto>
) {
}