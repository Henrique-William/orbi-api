package com.tech.orbi.entity;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "tb_routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company companyId;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver driverId;

    @ManyToOne
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicleId;

    private String description;

    private Date routeDate;

    private Date startDate;

    private Date endDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Company companyId) {
        this.companyId = companyId;
    }

    public Driver getDriverId() {
        return driverId;
    }

    public void setDriverId(Driver driverId) {
        this.driverId = driverId;
    }

    public Vehicle getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Vehicle vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getRouteDate() {
        return routeDate;
    }

    public void setRouteDate(Date routeDate) {
        this.routeDate = routeDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public enum Status {

        COLLECT(1L),
        TODELIVERY(2L),
        INROUTE(3L),
        RECEIVED(4L);

        long id;

        Status(Long id) {
            this.id = id;
        }

        public Long getId() {

            return id;

        }

    }

}
