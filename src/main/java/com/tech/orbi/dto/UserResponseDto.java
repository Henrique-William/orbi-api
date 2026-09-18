package com.tech.orbi.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String name,
        String email,
        String phone,
        LocalDateTime createdAt
) {
}