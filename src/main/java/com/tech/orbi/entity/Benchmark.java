package com.tech.orbi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "benchmark")
public class Benchmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "scenario_code", length = 20)
    private String scenarioCode;

    @Column(name = "scenario_name", nullable = false)
    private String scenarioName;

    @Column(name = "scenario_description", columnDefinition = "TEXT")
    private String scenarioDescription;

    @Column(name = "stops_count", nullable = false)
    private Integer stopsCount;

    @Column(name = "algorithm_name", nullable = false, length = 100)
    private String algorithmName;

    @Column(name = "algorithm_type", length = 100)
    private String algorithmType;

    @Column(name = "complexity", length = 60)
    private String complexity;

    /**
     * Tempo de computação puro do algoritmo em milissegundos.
     * NÃO inclui tempo de gravação no banco, consultas ou operações de rede.
     */
    @Column(name = "execution_time_ms", nullable = false)
    private Double executionTimeMs;

    @Column(name = "total_distance_km", nullable = false)
    private Double totalDistanceKm;

    @Column(name = "total_duration_min", nullable = false)
    private Double totalDurationMin;

    @Column(name = "total_cost", nullable = false)
    private Double totalCost;

    @Column(name = "avg_surface_factor", nullable = false)
    private Double avgSurfaceFactor;

    @Column(name = "efficiency_gain_pct")
    private Double efficiencyGainPct;

    /**
     * Tempo da rota inicial na ordem de cadastro (Sem otimização) em minutos.
     */
    @Column(name = "initial_duration_min", nullable = false)
    private Double initialDurationMin;

    /**
     * Distância da rota inicial na ordem de cadastro (Sem otimização) em km.
     */
    @Column(name = "initial_distance_km", nullable = false)
    private Double initialDistanceKm;

    @Column(name = "tour_order_json", columnDefinition = "TEXT")
    private String tourOrderJson;

    @Column(name = "route_coordinates_json", columnDefinition = "LONGTEXT")
    private String routeCoordinatesJson;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
