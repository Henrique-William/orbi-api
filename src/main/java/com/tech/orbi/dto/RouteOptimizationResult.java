package com.tech.orbi.dto;

import java.util.List;

/**
 * Resultado da otimização de rota via algoritmo TSP (Vizinho Mais Próximo).
 *
 * @param optimizedRoute  Lista ordenada dos pontos na sequência otimizada
 * @param totalDistanceKm Distância total da rota em quilômetros (Haversine)
 * @param totalCostBrl    Custo total estimado da rota em R$ (combustível + paradas + penalidades)
 */
public record RouteOptimizationResult(
        List<LocationDto> optimizedRoute,
        double totalDistanceKm,
        double totalCostBrl
) {}
