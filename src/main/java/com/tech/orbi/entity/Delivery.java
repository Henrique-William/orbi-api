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

    // Address and coordinates
    @Column(name = "pickup_address", columnDefinition = "TEXT")
    private String pickupAddress;
    @Column(name = "pickup_latitude", precision = 10, scale = 8)
    private BigDecimal pickupLatitude;
    @Column(name = "pickup_longitude", precision = 11, scale = 8)
    private BigDecimal pickupLongitude;


    @Column(name = "dropoff_address", columnDefinition = "TEXT")
    private String dropoffAddress;
    @Column(name = "dropoff_latitude", precision = 10, scale = 8)
    private BigDecimal dropoffLatitude;
    @Column(name = "dropoff_longitude", precision = 11, scale = 8)
    private BigDecimal dropoffLongitude;

    @Column(name = "recipient_name")
    private String recipientName;
    @Column(name = "recipient_phone")
    private String recipientPhone;

    // Details
    @Column(name = "package_details", columnDefinition = "TEXT")
    private String packageDetails;
    @Column(name = "estimated_fare", precision = 10, scale = 2)
    private BigDecimal estimatedFare;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    // Timestamps
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    public Delivery() {}

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
        status = DeliveryStatus.REQUESTED;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getDriver() {
        return driver;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(DeliveryStatus status) {
        this.status = status;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public BigDecimal getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(BigDecimal pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public BigDecimal getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(BigDecimal pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public String getDropoffAddress() {
        return dropoffAddress;
    }

    public void setDropoffAddress(String dropoffAddress) {
        this.dropoffAddress = dropoffAddress;
    }

    public BigDecimal getDropoffLatitude() {
        return dropoffLatitude;
    }

    public void setDropoffLatitude(BigDecimal dropoffLatitude) {
        this.dropoffLatitude = dropoffLatitude;
    }

    public BigDecimal getDropoffLongitude() {
        return dropoffLongitude;
    }

    public void setDropoffLongitude(BigDecimal dropoffLongitude) {
        this.dropoffLongitude = dropoffLongitude;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getPackageDetails() {
        return packageDetails;
    }

    public void setPackageDetails(String packageDetails) {
        this.packageDetails = packageDetails;
    }

    public BigDecimal getEstimatedFare() {
        return estimatedFare;
    }

    public void setEstimatedFare(BigDecimal estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getPickedUpAt() {
        return pickedUpAt;
    }

    public void setPickedUpAt(LocalDateTime pickedUpAt) {
        this.pickedUpAt = pickedUpAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
