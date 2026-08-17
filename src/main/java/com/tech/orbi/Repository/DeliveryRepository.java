package com.tech.orbi.Repository;

import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, Integer> {
    List<Delivery> findByRecipientEmail(String recipientEmail);

    @Query("SELECT d FROM Delivery d WHERE d.route.driverProfile.userId = :driverId AND d.status = :status")
    List<Delivery> findByDriverIdAndStatus(@Param("driverId") UUID driverId, @Param("status") DeliveryStatus status);
}