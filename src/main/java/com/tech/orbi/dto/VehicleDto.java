package com.tech.orbi.dto;

public record VehicleDto(
        Integer id,
        String licensePlate,
        String brand,
        String model,
        String color,
        Integer year,
        boolean isDefault
) {
}