package com.tech.orbi.Repository;

import com.tech.orbi.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanieRepository extends JpaRepository<Company,Long> {
        Optional<Company> findByName(String name);
}
