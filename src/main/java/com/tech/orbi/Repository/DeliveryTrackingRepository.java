package com.tech.orbi.Repository;

import com.tech.orbi.entity.DeliveryTracking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryTrackingRepository extends JpaRepository<DeliveryTracking, Long> {
}
