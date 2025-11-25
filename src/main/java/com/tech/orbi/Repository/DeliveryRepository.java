package com.tech.orbi.Repository;

import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRepository extends JpaRepository<Delivery, Integer> {
    List<Delivery> findByRecipientEmail(String recipientEmail);
    List<Delivery> findByDriverIdAndStatus(UUID driverId, DeliveryStatus status);
}
