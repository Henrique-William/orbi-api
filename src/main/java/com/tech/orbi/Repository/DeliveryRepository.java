package com.tech.orbi.Repository;

import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @Modifying
    @Query("DELETE FROM Delivery d WHERE d.route.id = :routeId")
    void deleteByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT d FROM Delivery d WHERE d.route.id = :routeId ORDER BY d.order ASC")
    List<Delivery> findByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT d FROM Delivery d WHERE d.route.driver.userId = :driverId AND d.status = :status")
    List<Delivery> findByDriverIdAndStatus(UUID driverId, DeliveryStatus status);
}
