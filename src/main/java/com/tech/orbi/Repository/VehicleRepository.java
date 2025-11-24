package com.tech.orbi.Repository;

import com.tech.orbi.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, Integer> {
    List<Vehicle> findByDriverId(UUID driverId);
}
