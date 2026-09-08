package com.tech.orbi.dto;

import com.tech.orbi.entity.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryDto(
        Long id,
        int order,
        DeliveryStatus status,
        String address,
        LocalDateTime deliveredAt
) {}