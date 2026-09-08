package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "assigned_driver_id")
    private Driver assignedDriver;

    @OneToMany(mappedBy = "route")
    private List<Delivery> deliveries;

    @Column(name = "total_distance")
    private double totalDistance;

    @Column(name = "total_cost")
    private double totalCost;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User genereatedBy;

    @Column(name = "created_by")
    private User createdBy;


    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}