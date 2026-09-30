package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_packages")
public class Package {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description")
    private String description;

    @Column(name = "weight")
    private Double weight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    /**
     * Verifica se o peso do pacote está dentro do limite máximo permitido.
     *
     * @param maxWeight Peso máximo permitido em kg
     * @return true se o peso está dentro do limite, false caso contrário
     */
    public boolean verifyWeightLimit(Double maxWeight) {
        return this.weight != null && this.weight <= maxWeight;
    }
}
