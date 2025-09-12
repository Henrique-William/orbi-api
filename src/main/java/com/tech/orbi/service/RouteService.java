package com.tech.orbi.service;

import com.tech.orbi.dto.LocationDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RouteService {

    private double [][] buildDistanceMatrix(List<LocationDto> locations) {
        int size = locations.size();
        double[][] matrix = new double[size][size];

        //Simulação: Calcula a distância euclidiana simples (não realista para estradas)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 0;
                    continue;
                }
                LocationDto loc1 = locations.get(i);
                LocationDto loc2 = locations.get(j);
                // Distância Euclidiana (apenas para exemplo)
                double dist = Math.sqrt(Math.pow(loc1.latitude() - loc2.latitude(), 2) + Math.pow(loc1.longitude() - loc2.longitude(), 2));
                matrix[i][j] = dist;
            }
        }
        return matrix;
    }

    /**
     * Encontra a melhor rota usando o algoritmo do Vizinho Mais Próximo.
     * @param locations A lista de locais a serem visitados, incluindo o ponto de partida.
     * @param startIndex O índice do ponto de partida na lista.
     * @return Uma lista ordenada de locais representando a rota otimizada.
     */
    public List<LocationDto> findBestRoute(List<LocationDto> locations, int startIndex){
        if (locations == null || locations.size() < 2) {
            return locations;
        }

        // 1. Obter a Natriz de Distâncias
        double[][] distanceMatrix = buildDistanceMatrix(locations);
        int numLocations = locations.size();

        // 2. Inicializar variaveis do algoritmo
        List<LocationDto> optmizedRoute = new ArrayList<>();
        boolean[] visited = new boolean[numLocations];
        int currentLocationIndex = startIndex;

        // 3. Loop principal do algoritmo do Vizinho Mais Próximo (KNN)
        for (int i = 0; i < numLocations; i++) {
            //Adiciona o local à rota e marca como visitado
            optmizedRoute.add(locations.get(currentLocationIndex));
            visited[currentLocationIndex] = true;

            double minDistance = Double.MAX_VALUE;
            int nextLocationIndex = -1;

            // Encontra o vizinho não visitado mais próximo
            for (int j = 0; j < numLocations; j++) {
                if (!visited[j] && distanceMatrix[currentLocationIndex][j] < minDistance) {
                    minDistance = distanceMatrix[currentLocationIndex][j];
                    nextLocationIndex = j;
                }
            }

            // Move para o próximo local mais proximo
            if (nextLocationIndex != -1) {
                currentLocationIndex = nextLocationIndex;
            }
        }

        // 4. (Opcional) adcionar o ponto de partida ao fianl para fechar o ciclo
        optmizedRoute.add(locations.get(startIndex));

        return optmizedRoute;
    }

}
