package com.tech.orbi.Repository;

import com.tech.orbi.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, Integer> {
    List<Rating> findByRatedId(UUID userId);
}
