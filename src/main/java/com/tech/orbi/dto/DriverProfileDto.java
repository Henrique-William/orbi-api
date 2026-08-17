package com.tech.orbi.dto;

import com.tech.orbi.entity.ApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DriverProfileDto(
        UUID userId,
        String licenseNumber,
        ApprovalStatus approvalStatus,
        boolean isActive,
        BigDecimal averageRating,
        LocalDateTime profileCreatedAt,
        List<VehicleDto> vehicles
) {
}