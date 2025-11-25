package com.tech.orbi.Repository;

import com.tech.orbi.entity.ApprovalStatus;
import com.tech.orbi.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, Integer> {
    List<DriverProfile> findByApprovalStatus(ApprovalStatus status);
}
