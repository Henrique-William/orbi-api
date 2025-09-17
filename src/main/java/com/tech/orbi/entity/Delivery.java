package com.tech.orbi.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tb_deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private User driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @Column(name = "pickup_address", columnDefinition = "TEXT", nullable = false)
    private String pickupAddress;

    @Column(name = "pickup_latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal pickupLongitude;

    @Column(name = "dropoff_address", columnDefinition = "TEXT", nullable = false)
    private String dropoffAddress;

    @Column(name = "dropoff_latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal dropoffLatitude;

    @Column(name = "dropoff_longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal dropoffLongitude;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "package_details", columnDefinition = "TEXT")
    private String packageDetails;

    @Column(name = "estimated_fare", precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL)
    private List<DeliveryTracking> trackingPoints;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL)
    private List<Rating> ratings;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        status = DeliveryStatus.REQUESTED;
    }

}
