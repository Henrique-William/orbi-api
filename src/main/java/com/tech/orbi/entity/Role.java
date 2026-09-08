package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name")
    private String name;

    public enum Values {

        ADMIN(1L),
        BASIC(2L),
        DRIVER(3L);

        long roleId;

        Values(Long roleId) {
            this.roleId = roleId;
        }

    }

}
