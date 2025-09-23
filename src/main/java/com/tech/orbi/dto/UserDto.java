package com.tech.orbi.dto;

import com.tech.orbi.entity.DriverProfile;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Vehicle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserDto(UUID id, String name, String email, LocalDateTime createdAt,
                      DriverProfile driverProfile, List<Vehicle> vehicles, Set<Role> role) {
}
