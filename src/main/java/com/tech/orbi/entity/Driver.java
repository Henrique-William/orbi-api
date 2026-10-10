package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_driver_profiles")
public class Driver {

    @Id
    @Column(name = "driver_id")
    private UUID userId;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_cnh", nullable = false)
    private String cnh;

    @Column(name = "driver_phone")
    private String phone;

    @Column(name = "profile_created_at", updatable = false)
    private LocalDateTime profileCreatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tb_users_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;

    @PrePersist
    protected void onCreate() {
        profileCreatedAt = LocalDateTime.now();
    }

}