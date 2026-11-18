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

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid = true;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "VALID";

    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;

    /**
     * Tempo de computação puro do algoritmo em milissegundos.
     * Nulo caso o algoritmo tenha sido invalidado por estouro de memória ou tempo.
     */
    @Column(name = "execution_time_ms")
    private Double executionTimeMs;

    @Column(name = "total_distance_km")
    private Double totalDistanceKm;

    @Column(name = "total_duration_min")
    private Double totalDurationMin;

    @Column(name = "total_cost")
    private Double totalCost;

    @Column(name = "avg_surface_factor")
    private Double avgSurfaceFactor;

    @Column(name = "efficiency_gain_pct")
    private Double efficiencyGainPct;

    /**
     * Gap de otimalidade em relação à melhor solução ou ótimo exato (Z*).
     * Fórmula: ((Z_alg - Z*) / Z*) * 100%
     */
    @Column(name = "optimality_gap_pct")
    private Double optimalityGapPct;

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
