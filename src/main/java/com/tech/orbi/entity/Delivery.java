package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number")
    private int order;

    // Address and coordinates
    @Column(name = "address", columnDefinition = "TEXT")
    private String address;
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    // Timestamps
    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;


    @PrePersist
    protected void onCreate() {
        pickedUpAt = LocalDateTime.now();
        status = DeliveryStatus.AT_PICKUP;
    }

}