package com.tech.orbi.dto;

import com.tech.orbi.entity.DeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DeliveryDto(
        Long id,
        int order,
        DeliveryStatus status,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String recipientEmail,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt
) {
    public DeliveryDto(
            Long id,
            int order,
            DeliveryStatus status,
            String address,
            LocalDateTime pickedUpAt,
            LocalDateTime deliveredAt
    ) {
        this(id, order, status, address, null, null, null, pickedUpAt, deliveredAt);
    }
}
