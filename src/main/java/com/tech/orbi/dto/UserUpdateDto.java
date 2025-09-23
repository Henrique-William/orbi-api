package com.tech.orbi.dto;

import com.tech.orbi.entity.DriverProfile;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Vehicle;

import java.util.List;
import java.util.Set;

public record UserUpdateDto(String name, String email, DriverProfile driverProfile, List<Vehicle> vehicles, Set<Role> role) {
}
