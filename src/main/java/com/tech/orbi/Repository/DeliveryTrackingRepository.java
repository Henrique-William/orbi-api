package com.tech.orbi.Repository;

import com.tech.orbi.entity.DeliveryTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryTrackingRepository extends JpaRepository<DeliveryTracking, Long> {
    List<DeliveryTracking> findByDeliveryIdOrderByTimestampAsc(Integer deliveryId);
}
