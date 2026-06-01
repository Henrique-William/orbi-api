package com.tech.orbi.service;

import com.tech.orbi.dto.LocationDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    private double[][] buildCostMatrix(List<LocationDto> locations) {
        int size = locations.size();
        double[][] matrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                    continue;
                }
                LocationDto loc1 = locations.get(i);
                LocationDto loc2 = locations.get(j);

                // 1. Euclidean distance base (d_AB)
                double d_AB = Math.sqrt(Math.pow(loc1.latitude() - loc2.latitude(), 2)
                        + Math.pow(loc1.longitude() - loc2.longitude(), 2));

                // 2. Road penalty weight (P_via)
                double p_via = getViaQualityWeight(loc1, loc2);

                // 3. Fixed Costs (C_fixos)
                double c_fixos = getFixedCosts(loc1, loc2);

                // 4. Financial Weighting Equation
                matrix[i][j] = (d_AB * p_via) + c_fixos;
            }
        }
        return matrix;
    }

    private double getViaQualityWeight(LocationDto origin, LocationDto destination) {
        return 1.0;
    }

    private double getFixedCosts(LocationDto origin, LocationDto destination) {
        return 0.0;
    }

    /**
     * Encontra a melhor rota usando o algoritmo do Vizinho Mais Próximo.
     * @param locations A lista de locais a serem visitados.
     * @param startIndex O índice do ponto de partida.
     * @return Uma lista ordenada representando a rota otimizada.
     */
    public List<LocationDto> findBestRoute(List<LocationDto> locations, int startIndex) {
        if (locations == null || locations.size() < 2) {
            return locations;
        }

        // 1. Initialize the Cost Matrix
        double[][] costMatrix = buildCostMatrix(locations);
        int numLocations = locations.size();

        // 2. Initialize algorithm variables
        List<LocationDto> optimizedRoute = new ArrayList<>();
        boolean[] visited = new boolean[numLocations];
        int currentLocationIndex = startIndex;

        // 3. Main loop focused on the lowest financial cost.
        for (int i = 0; i < numLocations; i++) {
            optimizedRoute.add(locations.get(currentLocationIndex));
            visited[currentLocationIndex] = true;

            double minCost = Double.MAX_VALUE;
            int nextLocationIndex = -1;

            for (int j = 0; j < numLocations; j++) {
                if (!visited[j] && costMatrix[currentLocationIndex][j] < minCost) {
                    minCost = costMatrix[currentLocationIndex][j];
                    nextLocationIndex = j;
                }
            }

            if (nextLocationIndex != -1) {
                currentLocationIndex = nextLocationIndex;
            }
        }

        // 4. Return to the starting point.
        optimizedRoute.add(locations.get(startIndex));

        return optimizedRoute;
    }
}