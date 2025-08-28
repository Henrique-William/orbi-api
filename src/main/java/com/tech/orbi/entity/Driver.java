package com.tech.orbi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tb_drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "driver_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "company_id")
    private Company companyId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String driverCpf;

    private String driverPhone;

    private boolean isActive;

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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDriverCpf() {
        return driverCpf;
    }

    public void setDriverCpf(String driverCpf) {
        this.driverCpf = driverCpf;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public void setDriverPhone(String driverPhone) {
        this.driverPhone = driverPhone;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
