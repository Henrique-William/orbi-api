package com.tech.orbi.dto;

import java.util.UUID;

public record CreateRatingDto(
        Long deliveryId,
        UUID ratedUserId,
        Integer ratingValue,
        String comment
) {}
