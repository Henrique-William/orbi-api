package com.tech.orbi.dto;

import com.tech.orbi.entity.DriverProfile;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Vehicle;

import java.util.List;
import java.util.UUID;

public record UserDto(UUID id, String name, String email) {
}
