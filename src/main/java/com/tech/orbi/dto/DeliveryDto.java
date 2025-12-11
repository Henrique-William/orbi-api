package com.tech.orbi.dto;

import com.tech.orbi.entity.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryDto(
        Integer id,
        int order,
        DeliveryStatus status,
        String recipientName,
        String dropoffAddress,
        String packageDetails,
        LocalDateTime deliveredAt
) {}