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
    private Driver driver;

    @OneToMany(mappedBy = "route")
    private List<Delivery> deliveries;

    @Column(name = "total_distance")
    private double totalDistance;

    @Column(name = "total_cost")
    private double totalCost;

    @Column(name = "total_stops")
    private Integer totalStops;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RouteStatus status;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = RouteStatus.CREATED;
        }
    }
}
