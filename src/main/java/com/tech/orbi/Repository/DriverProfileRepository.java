package com.tech.orbi.Repository;

import com.tech.orbi.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Integer> {
}
