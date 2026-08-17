package com.tech.orbi.dto;

import com.tech.orbi.entity.ApprovalStatus;
import com.tech.orbi.entity.User;
import com.tech.orbi.entity.Vehicle;

import java.time.LocalDateTime;
import java.util.List;

public record DriverDto(User user, List<Vehicle> vehicleList, String licenseNumber, ApprovalStatus approvalStatus,
                        boolean isActive, Double averageRating, LocalDateTime profileCreatedAt) {
}
