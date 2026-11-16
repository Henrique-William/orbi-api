package com.tech.orbi.service;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteDirectionsDto;
import com.tech.orbi.dto.RouteDirectionsDto.ExtraSummary;
import com.tech.orbi.dto.RouteDirectionsDto.Point;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de Roteirização Logística da Orbi.
 *
 * Implementa a arquitetura descrita na Iniciação Científica (orbi_ic):
 * 1. Ponderação de Superfície Viária (Phi_ij) com Poda por Limite Inferior (Lower-Bound Pruning).
 * 2. Otimizador Combinatório Global fundamentado na meta-heurística
 *    ALNS Aperfeiçoado (Adaptive Large Neighborhood Search com Partida Quente, Shaw Viário e Busca Local 2-opt / Or-opt).
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final OpenRouteServiceClient orsClient;
    private final RouteRepository routeRepository;
    private final DeliveryRepository deliveryRepository;
    private final boolean surfaceWeightEnabled;

    // Cache em memória de fatores de pavimento entre coordenadas para evitar requisições repetidas
    private final Map<String, Double> surfaceCache = new ConcurrentHashMap<>();

    public RouteService(
            OpenRouteServiceClient orsClient,
            RouteRepository routeRepository,
            DeliveryRepository deliveryRepository,
            @Value("${orbi.route.surface-weight-enabled:true}") boolean surfaceWeightEnabled) {
        this.orsClient = orsClient;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.surfaceWeightEnabled = surfaceWeightEnabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public Route saveOptimizedRoute(Route route, List<Delivery> deliveries) {
        Route savedRoute = routeRepository.save(route);
        if (deliveries != null && !deliveries.isEmpty()) {
            for (Delivery delivery : deliveries) {
                delivery.setRoute(savedRoute);
            }
            deliveryRepository.saveAll(deliveries);
        }
        return savedRoute;
    }

    /**
     * Calcula a melhor rota utilizando o ALNS Aperfeiçoado (Adaptive Large Neighborhood Search),
     * conforme modelado e validado cientificamente no ecossistema Orbi (orbi_ic).
     */
    public List<LocationDto> findBestRoute(List<LocationDto> locations, int startIndex) {
        validateInput(locations, startIndex);
        int n = locations.size();
        if (n == 1) {
            return locations;
        }
        if (n == 2) {
            int other = 1 - startIndex;
            return List.of(locations.get(startIndex), locations.get(other), locations.get(startIndex));
        }

        // 1. Obtenção da Matriz de Distâncias Viárias Base via OpenRouteService (Dijkstra)
        double[][] distances = orsClient.getDistanceMatrix(locations);
        validateMatrix(distances, n);

        // 2. Construção da Matriz de Custo Ponderado C_ij com Poda por Limite Inferior (Lower-Bound Pruning)
        double[][] costMatrix = buildCostMatrixWithPruning(locations, distances);

        // 3. Execução do ALNS Aperfeiçoado (Partida Quente + Shaw Viário + Regret-2 + Busca Local 2-opt/Or-opt)
        int[] bestTour = solveImprovedAlns(locations, costMatrix, startIndex);

        // 4. Mapeamento dos índices do circuito ótimo para a lista de LocationDto
        List<LocationDto> optimizedRoute = new ArrayList<>(bestTour.length);
        for (int node : bestTour) {
            optimizedRoute.add(locations.get(node));
        }

        log.info("Rota otimizada com ALNS Aperfeiçoado para {} paradas. Início/Fim: {} ('{}')",
                n, startIndex, locations.get(startIndex).address());

        return optimizedRoute;
    }

    @Transactional(readOnly = true)
    public RouteDirectionsDto getDirections(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));

        List<Delivery> deliveries = route.getDeliveries();
        if (deliveries == null || deliveries.size() < 2) {
            throw new IllegalStateException("The route should have at least two deliveries");
        }

        List<Point> points = deliveries.stream()
                .sorted(Comparator.comparingInt(Delivery::getOrder))
                .map(this::toPoint)
                .toList();
        RouteDirectionsDto directions = orsClient.getDirections(points);

        return new RouteDirectionsDto(
                routeId,
                directions.distanceMeters(),
                directions.durationSeconds(),
                directions.geometry(),
                directions.segments(),
                directions.extras()
        );
    }

    // =========================================================================
    // 1. ETAPA 1: PONDERAÇÃO VIÁRIA E PODA POR LIMITE INFERIOR (LOWER-BOUND PRUNING)
    // =========================================================================
    private double[][] buildCostMatrixWithPruning(List<LocationDto> locations, double[][] distances) {
        int n = locations.size();
        double[][] cost = new double[n][n];

        for (int i = 0; i < n; i++) {
            cost[i][i] = 0.0;
            List<Integer> candidates = new ArrayList<>(n - 1);
            for (int j = 0; j < n; j++) {
                if (i != j) candidates.add(j);
            }

            final int from = i;
            candidates.sort(Comparator
                    .comparingDouble((Integer to) -> distances[from][to])
                    .thenComparingInt(Integer::intValue));

            double bestCostFound = Double.POSITIVE_INFINITY;

            for (int j : candidates) {
                double dist = distances[i][j];

                // Poda por Limite Inferior: Como Phi >= 1.00 => C_ij >= d_ij.
                // Se a distância física já excede o melhor custo viário já registrado na vizinhança,
                // podemos interromper consultas HTTP individuais com segurança.
                if (bestCostFound < Double.POSITIVE_INFINITY && dist >= bestCostFound && candidates.size() > 8) {
                    cost[i][j] = dist; // aproximação segura sem HTTP adicional
                    continue;
                }

                double roadFactor = 1.0;
                if (surfaceWeightEnabled) {
                    try {
                        roadFactor = getCachedRoadFactor(locations.get(i), locations.get(j));
                    } catch (Exception e) {
                        log.debug("Fallback para fator 1.0 entre {} e {}: {}", i, j, e.getMessage());
                        roadFactor = 1.0;
                    }
                }

                double weighted = dist * roadFactor;
                cost[i][j] = weighted;
                if (weighted < bestCostFound) {
                    bestCostFound = weighted;
                }
            }
        }
        return cost;
    }

    private double getCachedRoadFactor(LocationDto origin, LocationDto destination) {
        String key = String.format(Locale.US, "%.5f,%.5f->%.5f,%.5f",
                origin.latitude(), origin.longitude(), destination.latitude(), destination.longitude());

        return surfaceCache.computeIfAbsent(key, k -> getRoadFactor(origin, destination));
    }

    private double getRoadFactor(LocationDto origin, LocationDto destination) {
        RouteDirectionsDto directions = orsClient.getDirections(List.of(
                new Point(origin.latitude(), origin.longitude()),
                new Point(destination.latitude(), destination.longitude())
        ));
        if (directions == null || directions.extras() == null) {
            return 1.0;
        }
        var surface = directions.extras().get("surface");
        return calculateSurfaceFactor(surface == null ? null : surface.summary());
    }

    private double calculateSurfaceFactor(List<ExtraSummary> summary) {
        if (summary == null || summary.isEmpty()) {
            return 1.0;
        }

        double weightedFactors = 0.0;
        double totalDistance = 0.0;
        for (ExtraSummary item : summary) {
            if (item == null || !Double.isFinite(item.distanceMeters()) || item.distanceMeters() < 0) {
                throw new IllegalArgumentException("O resumo de superfície contém uma distância inválida.");
            }
            weightedFactors += item.distanceMeters() * factorFor(item.value());
            totalDistance += item.distanceMeters();
        }
        return totalDistance == 0.0 ? 1.0 : Math.max(1.0, weightedFactors / totalDistance);
    }

    private double factorFor(int surface) {
        return switch (surface) {
            case 1 -> 1.02;
            case 2 -> 1.20;
            case 3, 4 -> 1.00;
            case 5 -> 1.10;
            case 6 -> 1.05;
            case 7 -> 1.10;
            case 8, 9 -> 1.12;
            case 10 -> 1.15;
            case 11 -> 1.25;
            case 12 -> 1.20;
            case 13 -> 1.30;
            case 14 -> 1.08;
            case 15 -> 1.30;
            case 16 -> 1.20;
            case 17 -> 1.25;
            case 18 -> 1.15;
            default -> 1.00;
        };
    }

    // =========================================================================
    // 2. ETAPA 2: ALNS APERFEIÇOADO (SHAW VIÁRIO + REGRET-2 + BUSCA LOCAL 2-OPT/OR-OPT)
    // =========================================================================

    /**
     * Executa a meta-heurística ALNS Aperfeiçoado com:
     * (i) Partida Quente (Warm-Start via NN + 2-opt);
     * (ii) Operador Shaw Viário Asimétrico R_viário(i,j);
     * (iii) Operador de Reparo Regret-2;
     * (iv) Destruição controlada com q <= 7;
     * (v) Polimento por Busca Local 2-opt e Or-opt;
     * (vi) Preservação determinística do startNode como origem e destino.
     */
    private int[] solveImprovedAlns(List<LocationDto> locations, double[][] costMatrix, int startNode) {
        int n = costMatrix.length;
        Random rng = new Random(42L);

        // 1. Partida Quente (Warm-Start): Constrói rota gulosa NN e desentrelaça com 2-opt
        int[] initNn = solveNearestNeighborInitial(costMatrix, startNode);
        int[] currentTour = solveTwoOptFast(costMatrix, initNn);
        double currentCost = tourCost(currentTour, costMatrix);

        int[] bestTour = currentTour.clone();
        double bestCost = currentCost;

        // Pesos adaptativos para Roleta
        // Destruição: [D0] Random, [D1] Worst Detour, [D2] Shaw Viário
        double[] destroyWeights = { 1.0, 1.0, 1.0 };
        // Reparo: [R0] Greedy Insertion, [R1] Regret-2 Insertion
        double[] repairWeights  = { 1.0, 1.0 };

        double[] destroyScores  = new double[3];
        double[] repairScores   = new double[2];
        int[] destroyUses       = new int[3];
        int[] repairUses        = new int[2];

        // Parâmetros calibrados conforme Seção 3 da IC (orbi_ic)
        int maxIterations = (n <= 40) ? 200 : 130;
        int maxQ = Math.max(2, Math.min(7, (int) Math.round(n * 0.22))); // q <= 7
        double temp = currentCost * 0.05;
        double cooling = 0.980;
        double reactionFactor = 0.25;

        for (int iter = 1; iter <= maxIterations; iter++) {
            int dOp = rouletteSelect(destroyWeights, rng);
            int rOp = rouletteSelect(repairWeights, rng);
            destroyUses[dOp]++;
            repairUses[rOp]++;

            int q = 2 + rng.nextInt(Math.max(1, maxQ - 1));

            List<Integer> partialRoute = new ArrayList<>(n + 1);
            for (int node : currentTour) partialRoute.add(node);
            List<Integer> removedPool = new ArrayList<>(q);

            // Destruição da rota parcial
            applyAlnsDestroy(partialRoute, removedPool, q, dOp, costMatrix, rng);

            // Reconstrução via Operadores de Reparo
            applyAlnsRepair(partialRoute, removedPool, rOp, costMatrix);

            int[] candidateTour = new int[n + 1];
            for (int i = 0; i <= n; i++) candidateTour[i] = partialRoute.get(i);

            // Polimento periódico Or-opt + 2-opt
            if (iter % 35 == 0) {
                candidateTour = solveOrOptFast(costMatrix, solveTwoOptFast(costMatrix, candidateTour));
            }
            double candidateCost = tourCost(candidateTour, costMatrix);

            double reward = 0.0;
            if (candidateCost + 1e-9 < bestCost) {
                // Nova melhor solução global: aplica refinamento local intensivo
                candidateTour = solveOrOptFast(costMatrix, solveTwoOptFast(costMatrix, candidateTour));
                candidateCost = tourCost(candidateTour, costMatrix);

                bestCost = candidateCost;
                bestTour = candidateTour.clone();
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 15.0; // Ganho máximo de aprendizado por reforço
            } else if (candidateCost + 1e-9 < currentCost) {
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 8.0;
            } else if (rng.nextDouble() < Math.exp(-(candidateCost - currentCost) / Math.max(1e-5, temp))) {
                // Aceitação estocástica de diversificação
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 3.0;
            }

            destroyScores[dOp] += reward;
            repairScores[rOp] += reward;
            temp *= cooling;

            // Atualização adaptativa periódica dos pesos da roleta a cada 40 iterações
            if (iter % 40 == 0) {
                for (int i = 0; i < destroyWeights.length; i++) {
                    if (destroyUses[i] > 0) {
                        destroyWeights[i] = (1.0 - reactionFactor) * destroyWeights[i]
                                + reactionFactor * (destroyScores[i] / destroyUses[i]);
                    }
                    destroyScores[i] = 0.0;
                    destroyUses[i] = 0;
                }
                for (int i = 0; i < repairWeights.length; i++) {
                    if (repairUses[i] > 0) {
                        repairWeights[i] = (1.0 - reactionFactor) * repairWeights[i]
                                + reactionFactor * (repairScores[i] / repairUses[i]);
                    }
                    repairScores[i] = 0.0;
                    repairUses[i] = 0;
                }
            }
        }

        return bestTour;
    }

    // --- OPERADORES DE DESTRUIÇÃO (OMEGA-) ---
    private void applyAlnsDestroy(
            List<Integer> route,
            List<Integer> removedPool,
            int q,
            int dOp,
            double[][] costMatrix,
            Random rng) {
        if (dOp == 0) {
            // [D0] Random Removal: remove q nós aleatórios preservando o depósito (índice 0 e último)
            for (int c = 0; c < q && route.size() > 3; c++) {
                int idx = 1 + rng.nextInt(route.size() - 2);
                removedPool.add(route.remove(idx));
            }
        } else if (dOp == 1) {
            // [D1] Worst-Cost Detour Removal: arranca os nós com maior custo de desvio
            for (int c = 0; c < q && route.size() > 3; c++) {
                int worstIdx = 1;
                double maxDetour = Double.NEGATIVE_INFINITY;
                for (int idx = 1; idx < route.size() - 1; idx++) {
                    int prev = route.get(idx - 1);
                    int curr = route.get(idx);
                    int next = route.get(idx + 1);
                    double detour = costMatrix[prev][curr] + costMatrix[curr][next] - costMatrix[prev][next];
                    if (detour > maxDetour) {
                        maxDetour = detour;
                        worstIdx = idx;
                    }
                }
                removedPool.add(route.remove(worstIdx));
            }
        } else {
            // [D2] Shaw Removal Viário Asimétrico:
            // R_viario(i, j) = min(C_ij, C_ji) + 0.5 * |C_ij - C_ji|
            int seedIdx = 1 + rng.nextInt(route.size() - 2);
            int seedNode = route.remove(seedIdx);
            removedPool.add(seedNode);

            while (removedPool.size() < q && route.size() > 3) {
                int closestIdx = 1;
                double minRelatedness = Double.POSITIVE_INFINITY;
                for (int idx = 1; idx < route.size() - 1; idx++) {
                    int cand = route.get(idx);
                    double relatedness = Math.min(costMatrix[seedNode][cand], costMatrix[cand][seedNode])
                            + 0.5 * Math.abs(costMatrix[seedNode][cand] - costMatrix[cand][seedNode]);

                    if (relatedness < minRelatedness) {
                        minRelatedness = relatedness;
                        closestIdx = idx;
                    }
                }
                removedPool.add(route.remove(closestIdx));
            }
        }
    }

    // --- OPERADORES DE REPARO (OMEGA+) ---
    private void applyAlnsRepair(
            List<Integer> route,
            List<Integer> removedPool,
            int rOp,
            double[][] costMatrix) {
        if (rOp == 0) {
            // [R0] Greedy Insertion: insere na vaga de menor acréscimo de custo
            while (!removedPool.isEmpty()) {
                int bestPoolIdx = -1;
                int bestInsertPos = -1;
                double bestDelta = Double.POSITIVE_INFINITY;

                for (int p = 0; p < removedPool.size(); p++) {
                    int node = removedPool.get(p);
                    for (int pos = 0; pos < route.size() - 1; pos++) {
                        int u = route.get(pos);
                        int v = route.get(pos + 1);
                        double delta = costMatrix[u][node] + costMatrix[node][v] - costMatrix[u][v];
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            bestPoolIdx = p;
                            bestInsertPos = pos + 1;
                        }
                    }
                }
                int chosenNode = removedPool.remove(bestPoolIdx);
                route.add(bestInsertPos, chosenNode);
            }
        } else {
            // [R1] Regret-2 Insertion com Visão de Arrependimento:
            // Regret_2(v) = Delta_ins^(2)(v) - Delta_ins^(1)(v)
            while (!removedPool.isEmpty()) {
                int chosenPoolIdx = 0;
                int chosenInsertPos = 1;
                double maxRegret = Double.NEGATIVE_INFINITY;

                for (int p = 0; p < removedPool.size(); p++) {
                    int node = removedPool.get(p);
                    double firstBest = Double.POSITIVE_INFINITY;
                    double secondBest = Double.POSITIVE_INFINITY;
                    int bestPosForNode = 1;

                    for (int pos = 0; pos < route.size() - 1; pos++) {
                        int u = route.get(pos);
                        int v = route.get(pos + 1);
                        double delta = costMatrix[u][node] + costMatrix[node][v] - costMatrix[u][v];

                        if (delta < firstBest) {
                            secondBest = firstBest;
                            firstBest = delta;
                            bestPosForNode = pos + 1;
                        } else if (delta < secondBest) {
                            secondBest = delta;
                        }
                    }

                    double regret = (secondBest < Double.POSITIVE_INFINITY) ? (secondBest - firstBest) : firstBest;
                    if (regret > maxRegret) {
                        maxRegret = regret;
                        chosenPoolIdx = p;
                        chosenInsertPos = bestPosForNode;
                    }
                }
                int chosenNode = removedPool.remove(chosenPoolIdx);
                route.add(chosenInsertPos, chosenNode);
            }
        }
    }

    // --- REFINAMENTO LOCAL 2-OPT E OR-OPT ---
    private int[] solveTwoOptFast(double[][] costMatrix, int[] initialTour) {
        int[] tour = initialTour.clone();
        int n = tour.length - 1;
        double currentCost = tourCost(tour, costMatrix);
        boolean improved = true;

        while (improved) {
            improved = false;
            for (int i = 1; i < n - 1; i++) {
                for (int j = i + 1; j < n; j++) {
                    reverseSubSegment(tour, i, j);
                    double candCost = tourCost(tour, costMatrix);
                    if (candCost + 1e-9 < currentCost) {
                        currentCost = candCost;
                        improved = true;
                    } else {
                        reverseSubSegment(tour, i, j);
                    }
                }
            }
        }
        return tour;
    }

    private int[] solveOrOptFast(double[][] costMatrix, int[] initialTour) {
        int[] tour = initialTour.clone();
        int n = tour.length - 1;
        double currentCost = tourCost(tour, costMatrix);
        boolean improved = true;

        while (improved) {
            improved = false;
            outer:
            for (int segLen = 1; segLen <= Math.min(3, n - 2); segLen++) {
                for (int i = 1; i <= n - segLen; i++) {
                    int prev = tour[i - 1];
                    int first = tour[i];
                    int last = tour[i + segLen - 1];
                    int next = tour[i + segLen];
                    double gainRemove = costMatrix[prev][first] + costMatrix[last][next] - costMatrix[prev][next];

                    for (int ins = 0; ins < n; ins++) {
                        if (ins >= i - 1 && ins < i + segLen) continue;
                        int u = tour[ins];
                        int v = tour[ins + 1];
                        double costInsert = costMatrix[u][first] + costMatrix[last][v] - costMatrix[u][v];
                        if (costInsert + 1e-9 < gainRemove) {
                            tour = relocateSegment(tour, i, segLen, ins);
                            currentCost = tourCost(tour, costMatrix);
                            improved = true;
                            break outer;
                        }
                    }
                }
            }
        }
        return tour;
    }

    private int[] relocateSegment(int[] tour, int start, int len, int insertAfter) {
        int n = tour.length - 1;
        int[] result = new int[n + 1];
        int[] seg = new int[len];
        for (int i = 0; i < len; i++) seg[i] = tour[start + i];

        int[] remaining = new int[n - len];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            if (i < start || i >= start + len) {
                remaining[idx++] = tour[i];
            }
        }

        int targetInRemaining = insertAfter >= start ? insertAfter - len : insertAfter;
        int resIdx = 0;
        for (int i = 0; i <= targetInRemaining; i++) {
            result[resIdx++] = remaining[i];
        }
        for (int i = 0; i < len; i++) {
            result[resIdx++] = seg[i];
        }
        for (int i = targetInRemaining + 1; i < remaining.length; i++) {
            result[resIdx++] = remaining[i];
        }
        result[n] = result[0];
        return result;
    }

    private void reverseSubSegment(int[] tour, int left, int right) {
        while (left < right) {
            int tmp = tour[left];
            tour[left] = tour[right];
            tour[right] = tmp;
            left++;
            right--;
        }
    }

    private int rouletteSelect(double[] weights, Random rng) {
        double total = 0.0;
        for (double w : weights) total += w;
        double r = rng.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (r <= acc) return i;
        }
        return weights.length - 1;
    }

    private double tourCost(int[] tour, double[][] costMatrix) {
        double cost = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            cost += costMatrix[tour[i]][tour[i + 1]];
        }
        return cost;
    }

    private int[] solveNearestNeighborInitial(double[][] costMatrix, int startNode) {
        int n = costMatrix.length;
        boolean[] visited = new boolean[n];
        int[] tour = new int[n + 1];

        tour[0] = startNode;
        visited[startNode] = true;
        int current = startNode;

        for (int step = 1; step < n; step++) {
            int nextBest = -1;
            double minDist = Double.POSITIVE_INFINITY;
            for (int candidate = 0; candidate < n; candidate++) {
                if (!visited[candidate] && costMatrix[current][candidate] < minDist) {
                    minDist = costMatrix[current][candidate];
                    nextBest = candidate;
                }
            }
            if (nextBest == -1) {
                for (int i = 0; i < n; i++) {
                    if (!visited[i]) {
                        nextBest = i;
                        break;
                    }
                }
            }
            tour[step] = nextBest;
            visited[nextBest] = true;
            current = nextBest;
        }
        tour[n] = startNode;
        return tour;
    }

    // =========================================================================
    // VALIDAÇÕES E UTILITÁRIOS
    // =========================================================================
    private Point toPoint(Delivery delivery) {
        if (delivery == null) {
            throw new IllegalStateException("A rota contém uma entrega nula.");
        }
        BigDecimal latitude = delivery.getLatitude();
        BigDecimal longitude = delivery.getLongitude();
        if (latitude == null || longitude == null) {
            throw new IllegalStateException("A rota contém uma entrega sem coordenadas.");
        }

        Point point = new Point(latitude.doubleValue(), longitude.doubleValue());
        validateCoordinate(point.latitude(), point.longitude(), delivery.getAddress());
        return point;
    }

    private void validateInput(List<LocationDto> locations, int startIndex) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("A lista de localizações não pode ser nula ou vazia.");
        }
        if (startIndex < 0 || startIndex >= locations.size()) {
            throw new IllegalArgumentException("O índice inicial deve apontar para uma localização existente.");
        }
        if (locations.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("A lista de localizações não pode conter elementos nulos.");
        }
        for (LocationDto location : locations) {
            validateCoordinate(location.latitude(), location.longitude(), location.address());
        }
    }

    private void validateCoordinate(double latitude, double longitude, String address) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude inválida para a localização '" + address + "'.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude inválida para a localização '" + address + "'.");
        }
    }

    private void validateMatrix(double[][] matrix, int expectedSize) {
        if (matrix == null || matrix.length != expectedSize) {
            throw new IllegalStateException("A matriz de distâncias possui dimensões incompatíveis.");
        }
        for (double[] row : matrix) {
            if (row == null || row.length != expectedSize) {
                throw new IllegalStateException("A matriz de distâncias possui dimensões incompatíveis.");
            }
            for (double distance : row) {
                if (!Double.isFinite(distance) || distance < 0) {
                    throw new IllegalStateException("A matriz de distâncias contém um valor inválido.");
                }
            }
        }
    }
}
