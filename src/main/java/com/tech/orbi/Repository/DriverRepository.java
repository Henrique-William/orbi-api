package com.tech.orbi.Repository;

import com.tech.orbi.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
    boolean existsByCnh(String cnh);
    Optional<Driver> findByCnh(String cnh);
}
