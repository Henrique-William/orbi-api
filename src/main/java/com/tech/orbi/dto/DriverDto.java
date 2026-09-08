package com.tech.orbi.dto;

import com.tech.orbi.entity.User;

import java.time.LocalDateTime;
import java.util.List;

public record DriverDto(User user, String licenseNumber, LocalDateTime profileCreatedAt) {
}
