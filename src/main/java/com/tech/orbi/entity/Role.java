package com.tech.orbi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tb_roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    private String name;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return name;
    }

    public void setRoleName(String roleName) {
        name = roleName;
    }

    public enum Values {

        ADMIN(1L),
        BASIC(2L),
        CLIENT(3L);

        long roleId;

        Values(Long roleId) {
            this.roleId = roleId;
        }

        public Long getRoleId() {

            return roleId;

        }

    }

}
