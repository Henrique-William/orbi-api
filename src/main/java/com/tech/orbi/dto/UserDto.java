package com.tech.orbi.dto;

import com.tech.orbi.entity.Role;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserDto(UUID id, String name, String email, LocalDateTime createdAt,
                      Set<Role> role) {
}
