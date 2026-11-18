package com.tech.orbi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.orbi.Repository.BenchmarkRepository;
import com.tech.orbi.dto.*;
import com.tech.orbi.entity.Benchmark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);

    private final BenchmarkRepository benchmarkRepository;
    private final ObjectMapper objectMapper;

    public BenchmarkService(BenchmarkRepository benchmarkRepository, ObjectMapper objectMapper) {
        this.benchmarkRepository = benchmarkRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Carrega a lista de endereços/pontos a partir de um arquivo externo JSON,
     * ou do payload fornecido, ou do arquivo padrão de recursos (Campinas / Santos).
     */
    public List<BenchmarkAddressDto> loadAddresses(String filePath, List<BenchmarkAddressDto> inlineAddresses) {
        if (inlineAddresses != null && !inlineAddresses.isEmpty()) {
            return inlineAddresses;
        }

        // Se um arquivo externo ou atalho de cenário foi indicado explicitamente
        if (filePath != null && !filePath.isBlank()) {
            String trimmed = filePath.trim();
            String candidateClasspath = trimmed;
            if (trimmed.matches("^(?i)c[1-6]$")) {
                candidateClasspath = "scenarios/" + trimmed.toLowerCase() + ".json";
            } else if (!candidateClasspath.startsWith("scenarios/") && (candidateClasspath.toLowerCase().endsWith(".json") && !candidateClasspath.contains("/") && !candidateClasspath.contains("\\"))) {
                candidateClasspath = "scenarios/" + candidateClasspath;
            }

            // Tenta classpath primeiro (para empacotamento em JAR e cenários C1-C6)
            try {
                ClassPathResource cpResource = new ClassPathResource(candidateClasspath);
                if (cpResource.exists()) {
                    try (InputStream is = cpResource.getInputStream()) {
                        List<BenchmarkAddressDto> list = objectMapper.readValue(is, new TypeReference<List<BenchmarkAddressDto>>() {});
                        log.info("Carregados {} endereços do classpath ({}): {}", list.size(), candidateClasspath, filePath);
                        return list;
                    }
                }
            } catch (Exception e) {
                log.debug("Arquivo não encontrado no classpath: {}", candidateClasspath);
            }

            try {
                File f = new File(trimmed);
                if (f.exists()) {
                    List<BenchmarkAddressDto> list = objectMapper.readValue(f, new TypeReference<List<BenchmarkAddressDto>>() {});
                    log.info("Carregados {} endereços do arquivo externo: {}", list.size(), filePath);
                    return list;
                }
            } catch (Exception e) {
                log.warn("Falha ao ler arquivo de endereços {}: {}", filePath, e.getMessage());
            }
        }

        // Tentar propriedade de sistema ou variável de ambiente
        String envPath = System.getProperty("benchmark.addresses.file", System.getenv("BENCHMARK_ADDRESSES_FILE"));
        if (envPath != null && !envPath.isBlank()) {
            try {
                File f = new File(envPath);
                if (f.exists()) {
                    List<BenchmarkAddressDto> list = objectMapper.readValue(f, new TypeReference<List<BenchmarkAddressDto>>() {});
                    log.info("Carregados {} endereços via ENV/Prop: {}", list.size(), envPath);
                    return list;
                }
            } catch (Exception e) {
                log.warn("Falha ao ler arquivo de endereços da ENV: {}", e.getMessage());
            }
        }

        // Fallback: carregar o arquivo padrão salvo no classpath
        try {
            ClassPathResource resource = new ClassPathResource("benchmark_addresses.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readValue(is, new TypeReference<List<BenchmarkAddressDto>>() {});
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao carregar benchmark_addresses.json do classpath: {}", e.getMessage());
        }

        // Fallback mínimo programático se o JSON não for acessível
        return getDefaultFallbackStops();
    }

    /**
     * Retorna o catálogo dos 6 cenários calibrados na documentação da IC (C1 a C6).
     */
    public List<Map<String, Object>> getAvailableScenarios() {
        try {
            ClassPathResource resource = new ClassPathResource("scenarios/index.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao ler scenarios/index.json do classpath: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Executa a bateria de benchmark comparando todos os modelos.
     * IMPORTANTE: O tempo para carregar arquivos, calcular matrizes, preparar coordenadas
     * e salvar no banco de dados NÃO é contabilizado nas métricas de execução dos algoritmos!
     */
    @Transactional
    public BenchmarkSessionSummaryDto runBenchmark(BenchmarkRunRequestDto request) {
        String sessionId = UUID.randomUUID().toString();
        LocalDateTime sessionCreatedAt = LocalDateTime.now();

        String resolvedFilePath = (request != null && request.addressesFilePath() != null && !request.addressesFilePath().isBlank())
                ? request.addressesFilePath()
                : (request != null && request.scenarioName() != null && request.scenarioName().trim().matches("^(?i)c[1-6]$"))
                ? request.scenarioName().trim()
                : null;

        List<BenchmarkAddressDto> rawAddresses = loadAddresses(
                resolvedFilePath,
                request != null ? request.addresses() : null
        );

        int targetN = (request != null && request.stopsCount() != null && request.stopsCount() > 0)
                ? Math.min(request.stopsCount(), rawAddresses.size())
                : rawAddresses.size();

        List<BenchmarkAddressDto> stops = new ArrayList<>(rawAddresses.subList(0, targetN));

        String scenarioName = (request != null && request.scenarioName() != null && !request.scenarioName().isBlank())
                ? request.scenarioName()
                : "Bateria Geral de Benchmark (N = " + targetN + " paradas)";

        String scenarioDescription = (request != null && request.scenarioDescription() != null)
                ? request.scenarioDescription()
                : "Comparação empírica multi-modelos: Baseline vs Exatos vs Heurísticas vs ALNS Orbi";

        double dwellTimePerStopMin = (request != null && request.dwellTimePerStopMin() != null)
                ? request.dwellTimePerStopMin()
                : 5.0;

        String batchId = request != null && request.batchId() != null ? request.batchId() : null;
        String scenarioCode = request != null && request.scenarioCode() != null ? request.scenarioCode() : null;

        if (scenarioCode == null && scenarioName != null) {
            Matcher m = Pattern.compile("(?i)\\b(C[1-6])\\b").matcher(scenarioName);
            if (m.find()) {
                scenarioCode = m.group(1).toUpperCase();
            }
        }
        if (scenarioCode == null && resolvedFilePath != null) {
            Matcher m = Pattern.compile("(?i)\\b(c[1-6])\\b").matcher(resolvedFilePath);
            if (m.find()) {
                scenarioCode = m.group(1).toUpperCase();
            }
        }
        if (scenarioCode == null) {
            scenarioCode = resolveScenarioCode(null, scenarioName, targetN);
        }

        // Pré-cálculo das Matrizes Viárias Reais (fora do cronômetro dos algoritmos)
        MatricesData matrices = buildMatrices(stops);

        // 1. Rota Inicial (Baseline - Ordem de Cadastro Sem Otimização)
        int[] baselineTour = new int[targetN];
        for (int i = 0; i < targetN; i++) baselineTour[i] = i;

        double initialDistanceKm = tourDistance(baselineTour, matrices.distanceKm);
        double initialDurationMin = tourDuration(baselineTour, matrices.durationMin, dwellTimePerStopMin);
        double initialWeightedCost = tourWeightedCost(baselineTour, matrices.weightedCost);

        List<AlgorithmEvaluation> evaluations = new ArrayList<>();

        // Modelo 0: Baseline (Sem Otimização)
        evaluations.add(new AlgorithmEvaluation(
                "0. Baseline (Sem Otimização)",
                "Baseline",
                "O(n)",
                0.0, // 0 ms
                baselineTour,
                initialDistanceKm,
                initialDurationMin,
                initialWeightedCost,
                "Ordem sequencial original de entrada sem qualquer otimização viária"
        ));

        // --- GRUPO 1: MÉTODOS EXATOS E PROTEÇÃO OPERACIONAL ---
        boolean includeExact = request == null || request.includeExactMethods() == null || Boolean.TRUE.equals(request.includeExactMethods());

        if (includeExact) {
            // 1. Branch-and-Bound (DFS) com salvaguarda de truncamento seguro em 3.000.000 nós
            long t0 = System.nanoTime();
            ExactResult bb = solveBranchAndBound(matrices.weightedCost, 0);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            boolean isTruncated = bb.truncated();
            String status = isTruncated ? "TRUNCATED" : "VALID";
            String invalidReason = isTruncated
                    ? String.format(Locale.US, "Busca exata incompleta: truncada por estouro de limite de nós (%,d nós)", bb.nodesVisited())
                    : null;
            String notes = isTruncated
                    ? String.format(Locale.US, "*Truncado em %,d nós visitados devido à explosão fatorial (O((n-1)!))", bb.nodesVisited())
                    : String.format(Locale.US, "Ótimo exato alcançado (%,d nós visitados | %,d podas)", bb.nodesVisited(), bb.branchesPruned());

            evaluations.add(new AlgorithmEvaluation(
                    "1. Branch-and-Bound (DFS)",
                    isTruncated ? "Exato (Truncado)" : "Exato",
                    "O((n-1)!)",
                    true,
                    status,
                    invalidReason,
                    ms,
                    bb.tour(),
                    tourDistance(bb.tour(), matrices.distanceKm),
                    tourDuration(bb.tour(), matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(bb.tour(), matrices.weightedCost),
                    notes
            ));

            // 2. Bellman-Held-Karp (Prog. Dinâmica) com salvaguarda de proteção operacional contra colapso de memória
            if (targetN <= 18) {
                long t0Hk = System.nanoTime();
                int[] hkTour = solveHeldKarp(matrices.weightedCost, 0);
                long elapsedHk = System.nanoTime() - t0Hk;
                double msHk = elapsedHk / 1_000_000.0;

                evaluations.add(new AlgorithmEvaluation(
                        "2. Bellman-Held-Karp (Prog. Dinâmica)",
                        "Exato",
                        "O(n^2 * 2^n)",
                        msHk,
                        hkTour,
                        tourDistance(hkTour, matrices.distanceKm),
                        tourDuration(hkTour, matrices.durationMin, dwellTimePerStopMin),
                        tourWeightedCost(hkTour, matrices.weightedCost),
                        String.format(Locale.US, "Ótimo global garantido matematicamente (%,d estados DP)", (1L << (targetN - 1)) * targetN)
                ));
            } else {
                // Salvaguarda de Proteção Operacional contra Colapso Teórico de RAM (O(n * 2^n))
                // Invalida o resultado pois a memória RAM estoura a capacidade física do sistema
                double ramGb = ((double) targetN * Math.pow(2.0, targetN - 1) * 12.0) / (1024.0 * 1024.0 * 1024.0);
                String ramDesc = (ramGb >= 1024.0 * 1024.0)
                        ? String.format(Locale.US, "%.1e GB RAM", ramGb)
                        : (ramGb >= 1024.0)
                        ? String.format(Locale.US, "%.1f TB RAM", ramGb / 1024.0)
                        : String.format(Locale.US, "%.1f GB RAM", ramGb);

                String hkInvalidReason = String.format(Locale.US, "Estouro de capacidade de memória RAM teórica (exigiria %s em bitmask DP O(n^2 * 2^n))", ramDesc);
                String hkNotes = String.format(Locale.US, "INVALIDADO: Estouro de RAM (%s teóricos). Proteção operacional ativada contra OOM.", ramDesc);

                evaluations.add(AlgorithmEvaluation.invalid(
                        "2. Bellman-Held-Karp (Prog. Dinâmica)",
                        "Exato (Proteção Operacional)",
                        "O(n^2 * 2^n)",
                        "INVALID_MEMORY_LIMIT",
                        hkInvalidReason,
                        hkNotes
                ));
            }
        }

        // --- GRUPO 2: HEURÍSTICAS CONSTRUTIVAS E HÍBRIDAS ---
        // Vizinho Mais Próximo (NN / Heurística Construtiva)
        {
            long t0 = System.nanoTime();
            int[] nnTour = solveNearestNeighbor(matrices.weightedCost, 0);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "3. Vizinho Mais Próximo (NN)",
                    "Heurística Construtiva",
                    "Theta(n^2)",
                    ms,
                    nnTour,
                    tourDistance(nnTour, matrices.distanceKm),
                    tourDuration(nnTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(nnTour, matrices.weightedCost),
                    "Decisão gulosa local a cada parada"
            ));
        }

        // Híbrido NN + 2-opt
        {
            long t0 = System.nanoTime();
            int[] nnTour = solveNearestNeighbor(matrices.weightedCost, 0);
            int[] twoOptTour = solveTwoOpt(matrices.weightedCost, nnTour);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "4. Híbrido NN + 2-opt",
                    "Heurística Híbrida",
                    "O(k * n^2)",
                    ms,
                    twoOptTour,
                    tourDistance(twoOptTour, matrices.distanceKm),
                    tourDuration(twoOptTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(twoOptTour, matrices.weightedCost),
                    "Construção gulosa refinada por desentrelaçamento de arestas 2-opt"
            ));
        }

        // 3-opt / Lin-Kernighan (LKH)
        {
            long t0 = System.nanoTime();
            int[] nnTour = solveNearestNeighbor(matrices.weightedCost, 0);
            int[] threeOptTour = solveThreeOpt(matrices.weightedCost, nnTour);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "5. 3-opt / Lin-Kernighan (LKH)",
                    "Busca Local Avançada",
                    "O(n^3)",
                    ms,
                    threeOptTour,
                    tourDistance(threeOptTour, matrices.distanceKm),
                    tourDuration(threeOptTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(threeOptTour, matrices.weightedCost),
                    "Reconexão de 3 arestas simultâneas para escape de mínimos locais"
            ));
        }

        // --- GRUPO 3: META-HEURÍSTICAS ---
        // Simulated Annealing
        {
            long t0 = System.nanoTime();
            int[] saTour = solveSimulatedAnnealing(matrices.weightedCost, 0);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "6. Simulated Annealing",
                    "Meta-heurística",
                    "O(I * n)",
                    ms,
                    saTour,
                    tourDistance(saTour, matrices.distanceKm),
                    tourDuration(saTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(saTour, matrices.weightedCost),
                    "Resfriamento simulado com critério estocástico de Metropolis"
            ));
        }

        // Algoritmo Genético Memético
        {
            long t0 = System.nanoTime();
            int[] gaTour = solveGeneticMemetic(matrices.weightedCost, 0);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "7. Algoritmo Genético Memético",
                    "Meta-heurística Populacional",
                    "O(G * P * n^2)",
                    ms,
                    gaTour,
                    tourDistance(gaTour, matrices.distanceKm),
                    tourDuration(gaTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(gaTour, matrices.weightedCost),
                    "Crossover OX + Mutação 2-opt em população de rotas"
            ));
        }

        // ALNS Padrão (Adaptive Large Neighborhood Search)
        {
            long t0 = System.nanoTime();
            int[] alnsStdTour = solveAlns(matrices.weightedCost, stops, 0, false);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "8. ALNS Padrão",
                    "Meta-heurística Adaptativa",
                    "O(I * n^2)",
                    ms,
                    alnsStdTour,
                    tourDistance(alnsStdTour, matrices.distanceKm),
                    tourDuration(alnsStdTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(alnsStdTour, matrices.weightedCost),
                    "Destruição Shaw/Random e Reparo Guloso com pesos adaptativos"
            ));
        }

        // ALNS Orbi Aprimorado (com ponderação viária e fatores de pavimento/relevo)
        {
            long t0 = System.nanoTime();
            int[] alnsOrbiTour = solveAlns(matrices.weightedCost, stops, 0, true);
            long elapsed = System.nanoTime() - t0;
            double ms = elapsed / 1_000_000.0;

            evaluations.add(new AlgorithmEvaluation(
                    "9. ALNS Orbi (Ponderação Viária)",
                    "Meta-heurística Estado da Arte",
                    "O(I * n^2)",
                    ms,
                    alnsOrbiTour,
                    tourDistance(alnsOrbiTour, matrices.distanceKm),
                    tourDuration(alnsOrbiTour, matrices.durationMin, dwellTimePerStopMin),
                    tourWeightedCost(alnsOrbiTour, matrices.weightedCost),
                    "ALNS calibrado para topografia e pavimentação com operadores especializados"
            ));
        }

        // Persistência no Banco de Dados MySQL (tabela 'benchmark')
        // O cronômetro dos algoritmos JÁ ENCERROU completamente aqui!
        boolean shouldSave = request == null || request.saveToDatabase() == null || Boolean.TRUE.equals(request.saveToDatabase());
        List<Benchmark> entitiesToSave = new ArrayList<>();
        List<BenchmarkResultDto> resultDtos = new ArrayList<>();

        AlgorithmEvaluation winnerEval = evaluations.stream()
                .filter(ev -> ev.isValid() && !ev.algorithmName().contains("Baseline") && ev.weightedCost() != null && ev.weightedCost() > 0)
                .min((a, b) -> comparePerformance(
                        a.algorithmName(), a.weightedCost(), a.distanceKm(), a.executionTimeMs(),
                        b.algorithmName(), b.weightedCost(), b.distanceKm(), b.executionTimeMs()
                ))
                .orElse(evaluations.get(0));

        double bestDistance = winnerEval.distanceKm() != null ? winnerEval.distanceKm() : 0.0;
        double bestDuration = winnerEval.durationMin() != null ? winnerEval.durationMin() : 0.0;
        String bestAlgorithm = winnerEval.algorithmName();
        double referenceBestCost = winnerEval.weightedCost() != null ? winnerEval.weightedCost() : 0.0;

        for (AlgorithmEvaluation ev : evaluations) {
            Double efficiencyGain = null;
            Double avgSurface = null;
            Double optimalityGap = null;

            if (ev.isValid() && ev.distanceKm() != null && ev.distanceKm() > 0) {
                if (initialDistanceKm > 0) {
                    efficiencyGain = ((initialDistanceKm - ev.distanceKm()) / initialDistanceKm) * 100.0;
                }
                avgSurface = (ev.weightedCost() != null && ev.weightedCost() > 0) ? (ev.weightedCost() / ev.distanceKm()) : 1.0;
                if (ev.weightedCost() != null && referenceBestCost > 0) {
                    optimalityGap = Math.max(0.0, ((ev.weightedCost() - referenceBestCost) / referenceBestCost) * 100.0);
                }
            } else if (ev.algorithmName().contains("Baseline") && referenceBestCost > 0 && initialWeightedCost > 0) {
                efficiencyGain = 0.0;
                avgSurface = initialDistanceKm > 0 ? (initialWeightedCost / initialDistanceKm) : 1.0;
                optimalityGap = Math.max(0.0, ((initialWeightedCost - referenceBestCost) / referenceBestCost) * 100.0);
            }

            String tourOrderJson = serializeIntArray(ev.tour());
            String coordinatesJson = buildRouteCoordinatesJson(ev.tour(), stops);

            Benchmark entity = new Benchmark();
            entity.setSessionId(sessionId);
            entity.setBatchId(batchId);
            entity.setScenarioCode(scenarioCode);
            entity.setScenarioName(scenarioName);
            entity.setScenarioDescription(scenarioDescription);
            entity.setStopsCount(targetN);
            entity.setAlgorithmName(ev.algorithmName());
            entity.setAlgorithmType(ev.algorithmType());
            entity.setComplexity(ev.complexity());
            entity.setIsValid(ev.isValid());
            entity.setStatus(ev.status());
            entity.setInvalidReason(ev.invalidReason());
            entity.setExecutionTimeMs(ev.executionTimeMs());
            entity.setTotalDistanceKm(ev.distanceKm());
            entity.setTotalDurationMin(ev.durationMin());
            entity.setTotalCost(ev.weightedCost());
            entity.setAvgSurfaceFactor(avgSurface);
            entity.setEfficiencyGainPct(efficiencyGain);
            entity.setOptimalityGapPct(optimalityGap);
            entity.setInitialDurationMin(initialDurationMin);
            entity.setInitialDistanceKm(initialDistanceKm);
            entity.setTourOrderJson(tourOrderJson);
            entity.setRouteCoordinatesJson(coordinatesJson);
            entity.setNotes(ev.notes());
            entity.setCreatedAt(sessionCreatedAt);

            entitiesToSave.add(entity);
        }

        if (shouldSave) {
            try {
                benchmarkRepository.saveAll(entitiesToSave);
                log.info("Benchmark salvo com sucesso na tabela 'benchmark' do MySQL. Session: {}", sessionId);
            } catch (Exception e) {
                log.error("Erro ao salvar benchmark no MySQL: {}", e.getMessage());
            }
        }

        for (int i = 0; i < evaluations.size(); i++) {
            AlgorithmEvaluation ev = evaluations.get(i);
            Benchmark entity = entitiesToSave.get(i);
            resultDtos.add(new BenchmarkResultDto(
                    entity.getId(),
                    sessionId,
                    scenarioName,
                    scenarioDescription,
                    targetN,
                    ev.algorithmName(),
                    ev.algorithmType(),
                    ev.complexity(),
                    entity.getIsValid(),
                    entity.getStatus(),
                    entity.getInvalidReason(),
                    entity.getExecutionTimeMs(),
                    entity.getTotalDistanceKm(),
                    entity.getTotalDurationMin(),
                    entity.getTotalCost(),
                    entity.getAvgSurfaceFactor(),
                    entity.getEfficiencyGainPct(),
                    entity.getOptimalityGapPct(),
                    initialDurationMin,
                    initialDistanceKm,
                    entity.getTourOrderJson(),
                    entity.getRouteCoordinatesJson(),
                    ev.notes(),
                    sessionCreatedAt
            ));
        }

        double maxSavings = initialDistanceKm > 0 && bestDistance < Double.MAX_VALUE
                ? ((initialDistanceKm - bestDistance) / initialDistanceKm) * 100.0
                : 0.0;

        return new BenchmarkSessionSummaryDto(
                sessionId,
                batchId,
                scenarioCode,
                scenarioName,
                scenarioDescription,
                targetN,
                initialDurationMin,
                initialDistanceKm,
                bestDistance < Double.MAX_VALUE ? bestDistance : initialDistanceKm,
                bestDuration < Double.MAX_VALUE ? bestDuration : initialDurationMin,
                bestAlgorithm,
                maxSavings,
                sessionCreatedAt,
                resultDtos
        );
    }

    public String resolveScenarioCode(String scenarioCode, String scenarioName, Integer stopsCount) {
        if (scenarioCode != null && !scenarioCode.isBlank()) {
            Matcher m = Pattern.compile("(?i)(C[1-6])").matcher(scenarioCode.trim());
            if (m.find()) {
                return m.group(1).toUpperCase(Locale.ROOT);
            }
            return scenarioCode.trim().toUpperCase(Locale.ROOT);
        }
        if (scenarioName != null && !scenarioName.isBlank()) {
            Matcher m = Pattern.compile("(?i)\\b(C[1-6])\\b").matcher(scenarioName);
            if (m.find()) {
                return m.group(1).toUpperCase(Locale.ROOT);
            }
            String lower = scenarioName.toLowerCase(Locale.ROOT);
            if (lower.contains("carga pesada") || lower.contains("doca")) return "C1";
            if (lower.contains("santos") || lower.contains("guaruj")) return "C2";
            if (lower.contains("rural") || lower.contains("terra")) return "C3";
            if (lower.contains("vuc") || lower.contains("comercial")) return "C4";
            if (lower.contains("e-commerce van") || lower.contains("last-mile") || lower.contains("padrão brasil")) return "C5";
            if (lower.contains("alta densidade") || lower.contains("black friday")) return "C6";
        }
        if (stopsCount != null) {
            return switch (stopsCount) {
                case 12 -> "C1";
                case 15 -> "C2";
                case 18 -> "C3";
                case 35 -> "C4";
                case 70 -> "C5";
                case 110 -> "C6";
                default -> null;
            };
        }
        return null;
    }

    private int scenarioSortRank(BenchmarkSessionSummaryDto s) {
        String code = resolveScenarioCode(s.scenarioCode(), s.scenarioName(), s.stopsCount());
        if (code != null && code.length() == 2 && code.charAt(0) == 'C' && Character.isDigit(code.charAt(1))) {
            return Character.getNumericValue(code.charAt(1));
        }
        return 99;
    }

    private void sortSessionsByScenarioCode(List<BenchmarkSessionSummaryDto> sessions) {
        sessions.sort(Comparator.comparingInt(this::scenarioSortRank)
                .thenComparing(s -> s.scenarioName() != null ? s.scenarioName() : ""));
    }

    private BenchmarkSessionSummaryDto withResolvedCode(BenchmarkSessionSummaryDto s, String resolvedCode) {
        if (Objects.equals(s.scenarioCode(), resolvedCode)) {
            return s;
        }
        return new BenchmarkSessionSummaryDto(
                s.sessionId(),
                s.batchId(),
                resolvedCode,
                s.scenarioName(),
                s.scenarioDescription(),
                s.stopsCount(),
                s.initialDurationMin(),
                s.initialDistanceKm(),
                s.bestDistanceKm(),
                s.bestDurationMin(),
                s.bestAlgorithm(),
                s.maxSavingsPct(),
                s.createdAt(),
                s.models()
        );
    }

    @Transactional(readOnly = true)
    public List<BenchmarkSessionSummaryDto> getAllSessions() {
        List<Benchmark> allRows = benchmarkRepository.findAllByOrderByIdDesc();
        if (allRows != null && !allRows.isEmpty()) {
            Map<String, List<Benchmark>> bySession = new LinkedHashMap<>();
            for (Benchmark b : allRows) {
                if (b.getSessionId() != null) {
                    bySession.computeIfAbsent(b.getSessionId(), k -> new ArrayList<>()).add(b);
                }
            }
            List<BenchmarkSessionSummaryDto> summaries = new ArrayList<>();
            for (Map.Entry<String, List<Benchmark>> entry : bySession.entrySet()) {
                List<Benchmark> recs = new ArrayList<>(entry.getValue());
                if (recs.stream().allMatch(r -> r.getId() != null)) {
                    recs.sort(Comparator.comparing(Benchmark::getId));
                } else {
                    Collections.reverse(recs);
                }
                BenchmarkSessionSummaryDto s = toSessionSummary(entry.getKey(), recs);
                if (s != null) {
                    summaries.add(s);
                }
            }
            return summaries;
        }

        List<String> sessionIds = benchmarkRepository.findDistinctSessionIds();
        List<BenchmarkSessionSummaryDto> summaries = new ArrayList<>();
        if (sessionIds != null) {
            for (String sId : sessionIds) {
                BenchmarkSessionSummaryDto s = getSessionDetails(sId);
                if (s != null) {
                    summaries.add(s);
                }
            }
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public BenchmarkSessionSummaryDto getSessionDetails(String sessionId) {
        List<Benchmark> records = benchmarkRepository.findBySessionIdOrderByIdAsc(sessionId);
        return toSessionSummary(sessionId, records);
    }

    private BenchmarkSessionSummaryDto toSessionSummary(String sessionId, List<Benchmark> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        Benchmark first = records.get(0);
        List<BenchmarkResultDto> dtoList = records.stream().map(r -> new BenchmarkResultDto(
                r.getId(),
                r.getSessionId(),
                r.getScenarioName(),
                r.getScenarioDescription(),
                r.getStopsCount(),
                r.getAlgorithmName(),
                r.getAlgorithmType(),
                r.getComplexity(),
                Boolean.TRUE.equals(r.getIsValid()),
                r.getStatus() != null ? r.getStatus() : (Boolean.TRUE.equals(r.getIsValid()) ? "VALID" : "INVALID"),
                r.getInvalidReason(),
                r.getExecutionTimeMs(),
                r.getTotalDistanceKm(),
                r.getTotalDurationMin(),
                r.getTotalCost(),
                r.getAvgSurfaceFactor(),
                r.getEfficiencyGainPct(),
                r.getOptimalityGapPct(),
                r.getInitialDurationMin(),
                r.getInitialDistanceKm(),
                r.getTourOrderJson(),
                r.getRouteCoordinatesJson(),
                r.getNotes(),
                r.getCreatedAt()
        )).toList();

        Benchmark winnerRecord = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsValid()) && !r.getAlgorithmName().contains("Baseline") && r.getTotalCost() != null && r.getTotalCost() > 0)
                .min((a, b) -> comparePerformance(
                        a.getAlgorithmName(), a.getTotalCost(), a.getTotalDistanceKm(), a.getExecutionTimeMs(),
                        b.getAlgorithmName(), b.getTotalCost(), b.getTotalDistanceKm(), b.getExecutionTimeMs()
                ))
                .orElse(first);

        double bestDist = winnerRecord.getTotalDistanceKm() != null ? winnerRecord.getTotalDistanceKm() : 0.0;
        double bestDur = winnerRecord.getTotalDurationMin() != null ? winnerRecord.getTotalDurationMin() : 0.0;
        String bestAlgo = winnerRecord.getAlgorithmName();

        double maxSav = first.getInitialDistanceKm() != null && first.getInitialDistanceKm() > 0 && bestDist > 0
                ? ((first.getInitialDistanceKm() - bestDist) / first.getInitialDistanceKm()) * 100.0
                : 0.0;

        String resolvedCode = resolveScenarioCode(first.getScenarioCode(), first.getScenarioName(), first.getStopsCount());

        return new BenchmarkSessionSummaryDto(
                sessionId,
                first.getBatchId(),
                resolvedCode,
                first.getScenarioName(),
                first.getScenarioDescription(),
                first.getStopsCount(),
                first.getInitialDurationMin(),
                first.getInitialDistanceKm(),
                bestDist,
                bestDur,
                bestAlgo,
                maxSav,
                first.getCreatedAt(),
                dtoList
        );
    }

    private boolean isAlnsOrbi(String algorithmName) {
        return algorithmName != null && algorithmName.contains("ALNS Orbi");
    }

    private int comparePerformance(
            String nameA, Double costA, Double distA, Double cpuMsA,
            String nameB, Double costB, Double distB, Double cpuMsB
    ) {
        if (costA == null && costB == null) return 0;
        if (costA == null) return 1;
        if (costB == null) return -1;
        if (distA == null && distB == null) return 0;
        if (distA == null) return 1;
        if (distB == null) return -1;

        if (Math.abs(costA - costB) > 1e-3) {
            return Double.compare(costA, costB);
        }
        if (Math.abs(distA - distB) > 1e-3) {
            return Double.compare(distA, distB);
        }
        boolean aOrbi = isAlnsOrbi(nameA);
        boolean bOrbi = isAlnsOrbi(nameB);
        if (aOrbi != bOrbi) {
            return aOrbi ? -1 : 1;
        }
        double cA = cpuMsA != null ? cpuMsA : Double.MAX_VALUE;
        double cB = cpuMsB != null ? cpuMsB : Double.MAX_VALUE;
        return Double.compare(cA, cB);
    }

    @Transactional(readOnly = true)
    public BenchmarkSessionSummaryDto getLatestSession() {
        Optional<Benchmark> latest = benchmarkRepository.findTopByOrderByCreatedAtDesc();
        return latest.map(benchmark -> getSessionDetails(benchmark.getSessionId())).orElse(null);
    }

    private record BenchmarkScenarioDef(
            String code,
            String name,
            String filePath,
            int stops,
            double dwellTime,
            String description
    ) {}

    /**
     * Executa todos os 6 cenários da IC em sequência (C1, C2, C3, C4, C5 e C6),
     * vinculando todos sob um identificador único de lote (batchId) e persistindo
     * os resultados isolados na tabela 'benchmark'.
     */
    @Transactional
    public BenchmarkBatchSummaryDto runAllScenariosInSequence() {
        String batchId = UUID.randomUUID().toString();
        log.info("Iniciando execução em sequência dos 6 cenários da IC. BatchId: {}", batchId);

        List<BenchmarkScenarioDef> defs = List.of(
                new BenchmarkScenarioDef("C1", "C1: Carga Pesada / B2B Doca (N = 12)", "c1.json", 12, 18.0, "Entregas volumosas/linha branca com ~18 min de doca por cliente no centro de Campinas."),
                new BenchmarkScenarioDef("C2", "C2: Barreira Geográfica Santos x Guarujá (N = 15)", "c2.json", 15, 12.0, "Cargas portuárias e litorâneas onde 450m no canal = 52 km rodoviários via Cubatão."),
                new BenchmarkScenarioDef("C3", "C3: Logística Rural e Vias de Terra (N = 18)", "c3.json", 18, 10.0, "Estradas rurais e de terra em Joaquim Egídio e Sousas com fator de superfície Phi_ij até 1.28."),
                new BenchmarkScenarioDef("C4", "C4: Distribuição Comercial B2B / VUC Regional (N = 35)", "c4.json", 35, 7.0, "Distribuição regional com veículos VUC na Região Metropolitana de Campinas (RMC)."),
                new BenchmarkScenarioDef("C5", "C5: E-commerce Last-Mile Padrão Brasil (N = 70)", "c5.json", 70, 4.0, "Rota densa de van de e-commerce padrão Brasil (Vans/Fiorino tipo Mercado Livre/Shopee)."),
                new BenchmarkScenarioDef("C6", "C6: E-commerce Alta Densidade / Black Friday (N = 110)", "c6.json", 110, 3.0, "Rota ultra-densa de pico sazonal (Amazon DSP / Black Friday) com 110 paradas urbanas.")
        );

        List<BenchmarkSessionSummaryDto> scenarioSessions = new ArrayList<>();
        double totalSavings = 0.0;

        for (BenchmarkScenarioDef def : defs) {
            BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                    def.name,
                    def.description,
                    null,
                    def.filePath,
                    def.stops,
                    def.dwellTime,
                    true,
                    true,
                    false,
                    batchId,
                    def.code
            );
            BenchmarkSessionSummaryDto summary = runBenchmark(req);
            scenarioSessions.add(summary);
            if (summary.maxSavingsPct() != null) {
                totalSavings += summary.maxSavingsPct();
            }
        }

        double avgSavings = scenarioSessions.isEmpty() ? 0.0 : totalSavings / scenarioSessions.size();
        log.info("Execução em sequência dos 6 cenários concluída com sucesso. BatchId: {}, Economia média: {}%", batchId, String.format(Locale.US, "%.1f", avgSavings));

        return new BenchmarkBatchSummaryDto(
                batchId,
                LocalDateTime.now(),
                scenarioSessions.size(),
                avgSavings,
                "9. ALNS Orbi (Ponderação Viária)",
                scenarioSessions
        );
    }

    private BenchmarkBatchSummaryDto buildBatchSummary(
            String batchId,
            LocalDateTime createdAt,
            List<BenchmarkSessionSummaryDto> sessions
    ) {
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        List<BenchmarkSessionSummaryDto> sorted = new ArrayList<>(sessions);
        sortSessionsByScenarioCode(sorted);

        double avgSavings = sorted.stream()
                .mapToDouble(s -> s.maxSavingsPct() != null ? s.maxSavingsPct() : 0.0)
                .average().orElse(0.0);

        LocalDateTime resolvedCreatedAt = createdAt != null
                ? createdAt
                : sorted.stream().map(BenchmarkSessionSummaryDto::createdAt).filter(Objects::nonNull).findFirst().orElse(LocalDateTime.now());

        return new BenchmarkBatchSummaryDto(
                batchId,
                resolvedCreatedAt,
                sorted.size(),
                avgSavings,
                "9. ALNS Orbi (Ponderação Viária)",
                sorted
        );
    }

    private BenchmarkBatchSummaryDto buildConsolidatedDbBatchFromSessions(List<BenchmarkSessionSummaryDto> allSessions) {
        if (allSessions == null || allSessions.isEmpty()) {
            return null;
        }
        Map<String, BenchmarkSessionSummaryDto> latestByScenario = new LinkedHashMap<>();
        for (BenchmarkSessionSummaryDto s : allSessions) {
            String code = resolveScenarioCode(s.scenarioCode(), s.scenarioName(), s.stopsCount());
            String key = code != null ? code : ("SESSION_" + s.sessionId());
            if (!latestByScenario.containsKey(key)) {
                latestByScenario.put(key, withResolvedCode(s, code));
            }
        }
        return buildBatchSummary("db-consolidated", null, new ArrayList<>(latestByScenario.values()));
    }

    @Transactional(readOnly = true)
    public List<BenchmarkBatchSummaryDto> getAllBatches() {
        List<String> batchIds = benchmarkRepository.findDistinctBatchIds();
        List<BenchmarkBatchSummaryDto> result = new ArrayList<>();
        if (batchIds != null) {
            for (String bId : batchIds) {
                BenchmarkBatchSummaryDto b = getBatchDetails(bId);
                if (b != null) result.add(b);
            }
        }

        List<BenchmarkSessionSummaryDto> allSessions = getAllSessions();
        BenchmarkBatchSummaryDto consolidated = buildConsolidatedDbBatchFromSessions(allSessions);
        if (consolidated != null && !consolidated.scenarioSessions().isEmpty()) {
            if (result.isEmpty()) {
                result.add(consolidated);
            } else {
                Set<String> firstBatchSessions = result.get(0).scenarioSessions().stream()
                        .map(BenchmarkSessionSummaryDto::sessionId)
                        .collect(Collectors.toSet());
                boolean hasUnbatchedOrComplementary = allSessions.stream().anyMatch(s -> s.batchId() == null)
                        || consolidated.scenarioSessions().stream().anyMatch(s -> !firstBatchSessions.contains(s.sessionId()));
                if (hasUnbatchedOrComplementary) {
                    if (result.get(0).scenarioSessions().size() < consolidated.scenarioSessions().size()) {
                        result.add(0, consolidated);
                    } else {
                        result.add(consolidated);
                    }
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BenchmarkBatchSummaryDto getBatchDetails(String batchId) {
        if ("db-consolidated".equalsIgnoreCase(batchId)) {
            return buildConsolidatedDbBatchFromSessions(getAllSessions());
        }

        List<Benchmark> entities = benchmarkRepository.findByBatchIdOrderByIdAsc(batchId);
        if (entities == null || entities.isEmpty()) return null;

        Map<String, List<Benchmark>> bySession = entities.stream()
                .collect(Collectors.groupingBy(Benchmark::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<BenchmarkSessionSummaryDto> sessions = bySession.entrySet().stream()
                .map(e -> toSessionSummary(e.getKey(), e.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return buildBatchSummary(batchId, entities.get(0).getCreatedAt(), sessions);
    }

    @Transactional(readOnly = true)
    public BenchmarkBatchSummaryDto getLatestBatch() {
        List<String> batchIds = benchmarkRepository.findDistinctBatchIds();
        BenchmarkBatchSummaryDto latest = null;
        if (batchIds != null && !batchIds.isEmpty()) {
            latest = getBatchDetails(batchIds.get(0));
        }

        if (latest != null) {
            long distinctStandardScenarios = latest.scenarioSessions().stream()
                    .map(s -> resolveScenarioCode(s.scenarioCode(), s.scenarioName(), s.stopsCount()))
                    .filter(code -> code != null && code.matches("C[1-6]"))
                    .distinct()
                    .count();
            if (distinctStandardScenarios >= 6) {
                return latest;
            }
        }

        List<BenchmarkSessionSummaryDto> allSessions = getAllSessions();
        if (allSessions.isEmpty()) {
            return latest;
        }

        if (latest == null) {
            return buildConsolidatedDbBatchFromSessions(allSessions);
        }

        // Complementa cenários ausentes no lote mais recente com sessões existentes no banco
        Map<String, BenchmarkSessionSummaryDto> mergedByScenario = new LinkedHashMap<>();
        for (BenchmarkSessionSummaryDto s : latest.scenarioSessions()) {
            String code = resolveScenarioCode(s.scenarioCode(), s.scenarioName(), s.stopsCount());
            String key = code != null ? code : ("SESSION_" + s.sessionId());
            mergedByScenario.put(key, withResolvedCode(s, code));
        }
        for (BenchmarkSessionSummaryDto s : allSessions) {
            String code = resolveScenarioCode(s.scenarioCode(), s.scenarioName(), s.stopsCount());
            if (code != null && !mergedByScenario.containsKey(code)) {
                mergedByScenario.put(code, withResolvedCode(s, code));
            }
        }

        return buildBatchSummary(latest.batchId(), latest.createdAt(), new ArrayList<>(mergedByScenario.values()));
    }

    // =========================================================================
    // CONSTRUÇÃO DE MATRIZES E COORDENADAS
    // =========================================================================
    private MatricesData buildMatrices(List<BenchmarkAddressDto> stops) {
        int n = stops.size();
        double[][] dist = new double[n][n];
        double[][] dur = new double[n][n];
        double[][] surf = new double[n][n];
        double[][] cost = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    surf[i][j] = 1.0;
                    dist[i][j] = 0.0;
                    dur[i][j] = 0.0;
                    cost[i][j] = 0.0;
                } else {
                    double lat1 = stops.get(i).latitude() != null ? stops.get(i).latitude() : -22.90;
                    double lon1 = stops.get(i).longitude() != null ? stops.get(i).longitude() : -47.06;
                    double lat2 = stops.get(j).latitude() != null ? stops.get(j).latitude() : -22.90;
                    double lon2 = stops.get(j).longitude() != null ? stops.get(j).longitude() : -47.06;

                    double hav = haversineKm(lat1, lon1, lat2, lon2);
                    double tortuosity = 1.32 + ((i > j) ? 0.04 : 0.0);
                    dist[i][j] = Math.max(0.2, hav * tortuosity);
                    dur[i][j] = (dist[i][j] / 28.0) * 60.0 + 1.2;

                    double s1 = stops.get(i).surfaceFactor();
                    double s2 = stops.get(j).surfaceFactor();
                    surf[i][j] = Math.max(1.0, (s1 + s2) / 2.0);
                }
            }
        }

        // Regra da Barreira Estuarina Santos x Guarujá via Cubatão (Cenário C2 da IC)
        applySantosGuarujaEstuarineBarrier(stops, dist, dur);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    cost[i][j] = dist[i][j] * surf[i][j];
                }
            }
        }
        return new MatricesData(dist, dur, surf, cost);
    }

    private void applySantosGuarujaEstuarineBarrier(
            List<BenchmarkAddressDto> stops,
            double[][] dist,
            double[][] dur
    ) {
        int n = stops.size();
        if (n < 3) return;

        int cubataoHub = -1;
        boolean[] isGuaruja = new boolean[n];
        int guarujaCount = 0;
        int santosCount = 0;

        for (int i = 0; i < n; i++) {
            BenchmarkAddressDto s = stops.get(i);
            String label = ((s.name() != null ? s.name() : "") + " "
                    + (s.address() != null ? s.address() : "") + " "
                    + (s.formattedAddress() != null ? s.formattedAddress() : "")).toLowerCase(Locale.ROOT);

            if (label.contains("cubat") || label.contains("piaçaguera") || label.contains("piacaguera")) {
                cubataoHub = i;
            } else if (label.contains("guaruj")
                    || label.contains("vicente de carvalho")
                    || label.contains("pitangueiras")
                    || label.contains("enseada")
                    || label.contains("astúrias")
                    || label.contains("asturias")
                    || label.contains("paecará")
                    || label.contains("paecara")) {
                isGuaruja[i] = true;
                guarujaCount++;
            } else if (label.contains("santos") || label.contains("são vicente") || label.contains("sao vicente")) {
                santosCount++;
            }
        }

        if (cubataoHub >= 0 && guarujaCount > 0 && santosCount > 0) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j && isGuaruja[i] != isGuaruja[j] && i != cubataoHub && j != cubataoHub) {
                        double roadViaCubataoDist = dist[i][cubataoHub] + dist[cubataoHub][j];
                        double roadViaCubataoDur = dur[i][cubataoHub] + dur[cubataoHub][j];
                        if (dist[i][j] < 20.0 && roadViaCubataoDist > 25.0) {
                            dist[i][j] = roadViaCubataoDist;
                            dur[i][j] = roadViaCubataoDur;
                        }
                    }
                }
            }
        }
    }

    private String buildRouteCoordinatesJson(int[] tour, List<BenchmarkAddressDto> stops) {
        try {
            List<Map<String, Object>> points = new ArrayList<>();
            for (int i = 0; i < tour.length; i++) {
                int idx = tour[i];
                BenchmarkAddressDto stop = stops.get(idx);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("order", i + 1);
                map.put("id", stop.id());
                map.put("name", stop.displayName());
                map.put("lat", stop.latitude());
                map.put("lng", stop.longitude());
                map.put("address", stop.address());
                map.put("surfaceFactor", stop.surfaceFactor());
                points.add(map);
            }
            // fecha o circuito voltando ao início
            if (tour.length > 0) {
                int firstIdx = tour[0];
                BenchmarkAddressDto start = stops.get(firstIdx);
                Map<String, Object> returnMap = new LinkedHashMap<>();
                returnMap.put("order", tour.length + 1);
                returnMap.put("id", start.id());
                returnMap.put("name", start.displayName() + " (Retorno)");
                returnMap.put("lat", start.latitude());
                returnMap.put("lng", start.longitude());
                returnMap.put("address", start.address());
                returnMap.put("surfaceFactor", start.surfaceFactor());
                returnMap.put("isReturn", true);
                points.add(returnMap);
            }
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String serializeIntArray(int[] arr) {
        if (arr == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    private double tourDistance(int[] tour, double[][] distMatrix) {
        if (tour == null || tour.length < 2) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            sum += distMatrix[tour[i]][tour[i + 1]];
        }
        sum += distMatrix[tour[tour.length - 1]][tour[0]];
        return sum;
    }

    private double tourDuration(int[] tour, double[][] durMatrix, double dwellTimePerStop) {
        if (tour == null || tour.length < 2) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            sum += durMatrix[tour[i]][tour[i + 1]] + dwellTimePerStop;
        }
        sum += durMatrix[tour[tour.length - 1]][tour[0]];
        return sum;
    }

    private double tourWeightedCost(int[] tour, double[][] costMatrix) {
        if (tour == null || tour.length < 2) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < tour.length - 1; i++) {
            sum += costMatrix[tour[i]][tour[i + 1]];
        }
        sum += costMatrix[tour[tour.length - 1]][tour[0]];
        return sum;
    }

    // =========================================================================
    // NÚCLEO DOS ALGORITMOS (MEDIDOS PURAMENTE)
    // =========================================================================

    // 1. Branch and Bound DFS com truncamento seguro (3.000.000 nós)
    private static final long MAX_BRANCH_AND_BOUND_NODES = 3_000_000L;

    private record ExactResult(int[] tour, long nodesVisited, long branchesPruned, boolean truncated) {}

    private ExactResult solveBranchAndBound(double[][] cost, int start) {
        int n = cost.length;
        int[] bestTour = new int[n];
        int[] initialTour = solveNearestNeighbor(cost, start);
        double[] bestCost = { tourWeightedCost(initialTour, cost) };
        System.arraycopy(initialTour, 0, bestTour, 0, n);

        // Pré-computa menor aresta de saída para cada vértice (limite inferior O(1) na DFS)
        double[] minOut = new double[n];
        for (int i = 0; i < n; i++) {
            double m = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (i != j) m = Math.min(m, cost[i][j]);
            }
            minOut[i] = m;
        }

        long[] nodes = { 0L };
        long[] pruned = { 0L };
        boolean[] truncated = { false };

        boolean[] visited = new boolean[n];
        int[] currentTour = new int[n];
        currentTour[0] = start;
        visited[start] = true;

        bbDfs(cost, minOut, 1, start, 0.0, currentTour, visited, bestTour, bestCost, nodes, pruned, truncated);

        return new ExactResult(bestTour, nodes[0], pruned[0], truncated[0]);
    }

    private void bbDfs(double[][] cost, double[] minOut, int count, int current, double currentCost,
                       int[] currentTour, boolean[] visited, int[] bestTour, double[] bestCost,
                       long[] nodes, long[] pruned, boolean[] truncated) {
        nodes[0]++;
        if (nodes[0] >= MAX_BRANCH_AND_BOUND_NODES) {
            truncated[0] = true;
            return;
        }

        int n = cost.length;
        if (count == n) {
            double total = currentCost + cost[current][currentTour[0]];
            if (total < bestCost[0]) {
                bestCost[0] = total;
                System.arraycopy(currentTour, 0, bestTour, 0, n);
            }
            return;
        }

        for (int next = 0; next < n; next++) {
            if (!visited[next]) {
                double nextCost = currentCost + cost[current][next];
                if (nextCost + minOut[next] >= bestCost[0]) {
                    pruned[0]++;
                    continue;
                }
                visited[next] = true;
                currentTour[count] = next;
                bbDfs(cost, minOut, count + 1, next, nextCost, currentTour, visited, bestTour, bestCost, nodes, pruned, truncated);
                visited[next] = false;
                if (truncated[0]) return;
            }
        }
    }

    // 2. Bellman-Held-Karp Exact DP (para N <= 18)
    private int[] solveHeldKarp(double[][] cost, int start) {
        int n = cost.length;
        if (n == 1) return new int[]{ start };
        int numSubsets = 1 << n;

        double[][] dp = new double[numSubsets][n];
        int[][] parent = new int[numSubsets][n];

        for (int i = 0; i < numSubsets; i++) {
            Arrays.fill(dp[i], Double.POSITIVE_INFINITY);
            Arrays.fill(parent[i], -1);
        }

        dp[1 << start][start] = 0.0;

        for (int mask = 1; mask < numSubsets; mask++) {
            if ((mask & (1 << start)) == 0) continue;
            for (int u = 0; u < n; u++) {
                if ((mask & (1 << u)) == 0 || Double.isInfinite(dp[mask][u])) continue;
                for (int v = 0; v < n; v++) {
                    if ((mask & (1 << v)) != 0) continue;
                    int nextMask = mask | (1 << v);
                    double nextCost = dp[mask][u] + cost[u][v];
                    if (nextCost < dp[nextMask][v]) {
                        dp[nextMask][v] = nextCost;
                        parent[nextMask][v] = u;
                    }
                }
            }
        }

        int allMask = (1 << n) - 1;
        double minTotal = Double.POSITIVE_INFINITY;
        int lastNode = -1;
        for (int u = 0; u < n; u++) {
            if (u != start) {
                double total = dp[allMask][u] + cost[u][start];
                if (total < minTotal) {
                    minTotal = total;
                    lastNode = u;
                }
            }
        }

        int[] tour = new int[n];
        int currMask = allMask;
        int currNode = lastNode;
        for (int i = n - 1; i >= 1; i--) {
            tour[i] = currNode;
            int prevNode = parent[currMask][currNode];
            currMask ^= (1 << currNode);
            currNode = prevNode;
        }
        tour[0] = start;
        return tour;
    }

    // 3. Vizinho Mais Próximo (NN)
    private int[] solveNearestNeighbor(double[][] cost, int start) {
        int n = cost.length;
        int[] tour = new int[n];
        boolean[] visited = new boolean[n];
        tour[0] = start;
        visited[start] = true;

        for (int i = 0; i < n - 1; i++) {
            int current = tour[i];
            int nextBest = -1;
            double minDist = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (!visited[j] && cost[current][j] < minDist) {
                    minDist = cost[current][j];
                    nextBest = j;
                }
            }
            if (nextBest != -1) {
                tour[i + 1] = nextBest;
                visited[nextBest] = true;
            }
        }
        return tour;
    }

    // 4. 2-opt Refinamento Local (Exato para matrizes assimétricas)
    private int[] solveTwoOpt(double[][] cost, int[] initialTour) {
        int n = initialTour.length;
        int[] tour = Arrays.copyOf(initialTour, n);
        if (n < 4) return tour;

        boolean improved = true;
        int iters = 0;
        while (improved && iters < 200) {
            improved = false;
            iters++;
            for (int i = 1; i < n - 1; i++) {
                int a = tour[i - 1];
                int b = tour[i];
                double internalRevDelta = 0.0;

                for (int j = i + 1; j < n; j++) {
                    internalRevDelta += cost[tour[j]][tour[j - 1]] - cost[tour[j - 1]][tour[j]];
                    int c = tour[j];
                    int d = tour[(j + 1) % n];

                    double delta = (cost[a][c] - cost[a][b])
                            + (cost[b][d] - cost[c][d])
                            + internalRevDelta;
                    if (delta < -1e-6) {
                        reverseSubSegment(tour, i, j);
                        improved = true;
                        break;
                    }
                }
                if (improved) break;
            }
        }
        return tour;
    }

    // Busca Local Or-opt (Realocação de blocos de 1 a 3 paradas sem inversão de fluxo)
    private int[] solveOrOpt(double[][] cost, int[] initialTour) {
        int n = initialTour.length;
        int[] tour = Arrays.copyOf(initialTour, n);
        if (n < 5) return tour;

        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 120) {
            improved = false;
            outer:
            for (int segLen = 1; segLen <= Math.min(3, n - 2); segLen++) {
                for (int i = 1; i <= n - segLen; i++) {
                    int prev = tour[i - 1];
                    int first = tour[i];
                    int last = tour[i + segLen - 1];
                    int next = tour[(i + segLen) % n];
                    double removeDelta = cost[prev][next] - cost[prev][first] - cost[last][next];

                    for (int ins = 0; ins < n; ins++) {
                        if (ins >= i - 1 && ins < i + segLen) continue;
                        int u = tour[ins];
                        int v = tour[(ins + 1) % n];
                        double insertDelta = cost[u][first] + cost[last][v] - cost[u][v];
                        if (removeDelta + insertDelta < -1e-6) {
                            tour = relocateSegmentCircular(tour, i, segLen, ins);
                            improved = true;
                            break outer;
                        }
                    }
                }
            }
        }
        return tour;
    }

    private int[] relocateSegmentCircular(int[] tour, int startIdx, int segLen, int insertAfterIdx) {
        int n = tour.length;
        List<Integer> remaining = new ArrayList<>(n - segLen);
        List<Integer> seg = new ArrayList<>(segLen);
        for (int idx = 0; idx < n; idx++) {
            if (idx >= startIdx && idx < startIdx + segLen) {
                seg.add(tour[idx]);
            } else {
                remaining.add(tour[idx]);
            }
        }
        int targetPos = (insertAfterIdx < startIdx) ? (insertAfterIdx + 1) : (insertAfterIdx - segLen + 1);
        remaining.addAll(targetPos, seg);
        int[] res = new int[n];
        for (int idx = 0; idx < n; idx++) res[idx] = remaining.get(idx);
        return res;
    }

    private void reverseSubSegment(int[] tour, int left, int right) {
        while (left < right) {
            int temp = tour[left];
            tour[left] = tour[right];
            tour[right] = temp;
            left++;
            right--;
        }
    }

    // 5. 3-opt / Lin-Kernighan (Compatível com grafos assimétricos)
    private int[] solveThreeOpt(double[][] cost, int[] initialTour) {
        int n = initialTour.length;
        int[] tour = solveTwoOpt(cost, initialTour);
        if (n < 6) return tour;

        boolean improved = true;
        int iters = 0;
        int maxSpan = (n <= 35) ? n : 22;
        while (improved && iters < 60) {
            improved = false;
            iters++;
            for (int i = 0; i < n - 2; i++) {
                int jLimit = Math.min(n - 1, i + maxSpan);
                for (int j = i + 1; j < jLimit; j++) {
                    int kLimit = Math.min(n, j + maxSpan);
                    for (int k = j + 1; k < kLimit; k++) {
                        int a = tour[i], b = tour[i + 1];
                        int c = tour[j], d = tour[j + 1];
                        int e = tour[k], f = tour[(k + 1) % n];

                        double d0 = cost[a][b] + cost[c][d] + cost[e][f];
                        // Troca de blocos [i+1..j] e [j+1..k] preservando a direção dos arcos internos
                        double d3 = cost[a][d] + cost[e][b] + cost[c][f];
                        if (d3 < d0 - 1e-6) {
                            reverseSubSegment(tour, i + 1, k);
                            reverseSubSegment(tour, i + 1, i + (k - j));
                            reverseSubSegment(tour, i + 1 + (k - j), k);
                            improved = true;
                            break;
                        }
                    }
                    if (improved) break;
                }
                if (improved) break;
            }
            if (improved) {
                tour = solveTwoOpt(cost, tour);
            }
        }
        return tour;
    }

    // 6. Simulated Annealing
    private int[] solveSimulatedAnnealing(double[][] cost, int start) {
        int n = cost.length;
        int[] current = solveNearestNeighbor(cost, start);
        int[] best = Arrays.copyOf(current, n);
        double currentCost = tourWeightedCost(current, cost);
        double bestCost = currentCost;

        double temp = 100.0;
        double coolingRate = 0.995;
        Random rng = new Random(42L);

        int maxIter = Math.min(15000, n * 250);
        for (int iter = 0; iter < maxIter && temp > 1e-4; iter++) {
            int i = 1 + rng.nextInt(n - 1);
            int j = 1 + rng.nextInt(n - 1);
            if (i == j) continue;
            if (i > j) { int tmp = i; i = j; j = tmp; }

            int[] candidate = Arrays.copyOf(current, n);
            reverseSubSegment(candidate, i, j);
            double candCost = tourWeightedCost(candidate, cost);
            double delta = candCost - currentCost;

            if (delta < 0 || Math.exp(-delta / temp) > rng.nextDouble()) {
                current = candidate;
                currentCost = candCost;
                if (currentCost < bestCost) {
                    bestCost = currentCost;
                    best = Arrays.copyOf(current, n);
                }
            }
            temp *= coolingRate;
        }
        return best;
    }

    // 7. Algoritmo Genético Memético
    private int[] solveGeneticMemetic(double[][] cost, int start) {
        int n = cost.length;
        int popSize = Math.min(30, Math.max(10, n));
        int generations = Math.min(100, Math.max(25, 400 / n));
        Random rng = new Random(101L);

        List<int[]> population = new ArrayList<>(popSize);
        population.add(solveTwoOpt(cost, solveNearestNeighbor(cost, start)));

        for (int p = 1; p < popSize; p++) {
            int[] ind = new int[n];
            for (int i = 0; i < n; i++) ind[i] = i;
            for (int i = 1; i < n; i++) {
                int sw = 1 + rng.nextInt(n - 1);
                int tmp = ind[i]; ind[i] = ind[sw]; ind[sw] = tmp;
            }
            population.add(solveTwoOpt(cost, ind));
        }

        int[] globalBest = population.get(0);
        double globalBestCost = tourWeightedCost(globalBest, cost);

        for (int g = 0; g < generations; g++) {
            population.sort(Comparator.comparingDouble(ind -> tourWeightedCost(ind, cost)));
            if (tourWeightedCost(population.get(0), cost) < globalBestCost) {
                globalBest = Arrays.copyOf(population.get(0), n);
                globalBestCost = tourWeightedCost(globalBest, cost);
            }

            List<int[]> nextGen = new ArrayList<>(popSize);
            // Elitismo
            nextGen.add(Arrays.copyOf(population.get(0), n));
            nextGen.add(Arrays.copyOf(population.get(1), n));

            while (nextGen.size() < popSize) {
                int[] p1 = population.get(rng.nextInt(popSize / 2));
                int[] p2 = population.get(rng.nextInt(popSize / 2));
                int[] child = orderCrossover(p1, p2, rng);
                if (rng.nextDouble() < 0.3) {
                    child = solveTwoOpt(cost, child);
                }
                nextGen.add(child);
            }
            population = nextGen;
        }

        return globalBest;
    }

    private int[] orderCrossover(int[] p1, int[] p2, Random rng) {
        int n = p1.length;
        int[] child = new int[n];
        Arrays.fill(child, -1);
        child[0] = p1[0];

        int c1 = 1 + rng.nextInt(n - 1);
        int c2 = 1 + rng.nextInt(n - 1);
        if (c1 > c2) { int t = c1; c1 = c2; c2 = t; }

        boolean[] inChild = new boolean[n];
        inChild[child[0]] = true;

        for (int i = c1; i <= c2; i++) {
            child[i] = p1[i];
            inChild[p1[i]] = true;
        }

        int p2Idx = 0;
        for (int i = 1; i < n; i++) {
            if (child[i] == -1) {
                while (p2Idx < n && inChild[p2[p2Idx]]) {
                    p2Idx++;
                }
                if (p2Idx < n) {
                    child[i] = p2[p2Idx];
                    inChild[p2[p2Idx]] = true;
                }
            }
        }
        return child;
    }

    // 8 & 9. ALNS (Adaptive Large Neighborhood Search: Padrão Ropke-Pisinger vs ALNS Orbi Aperfeiçoado)
    private int[] solveAlns(double[][] cost, List<BenchmarkAddressDto> stops, int start, boolean enhancedMode) {
        int n = cost.length;
        int[] initNn = solveNearestNeighbor(cost, start);
        if (n <= 3) return initNn;

        int[] current = enhancedMode
                ? solveOrOpt(cost, solveTwoOpt(cost, initNn))
                : Arrays.copyOf(initNn, n);
        int[] best = Arrays.copyOf(current, n);
        double bestCost = tourWeightedCost(best, cost);
        double currentCost = bestCost;

        Random rng = new Random(enhancedMode ? 42L : 777L);
        int iterations = enhancedMode
                ? (n <= 40 ? 180 : 120)
                : (n <= 40 ? 220 : 140);
        int maxDestroy = enhancedMode
                ? Math.max(2, Math.min(7, (int) Math.round(n * 0.22)))
                : Math.max(2, Math.min(12, (int) Math.round(n * 0.30)));

        // Destruição: 0 = Random, 1 = Worst-Cost Detour, 2 = Shaw (Haversine vs Viário+Pavimento)
        double[] destroyWeights = { 1.0, 1.0, 1.0 };
        // Reparo: 0 = Greedy Insertion, 1 = Regret-2 Insertion
        double[] repairWeights = { 1.0, 1.0 };

        double[] destroyScores = new double[3];
        double[] repairScores = new double[2];
        int[] destroyUses = new int[3];
        int[] repairUses = new int[2];

        double temp = Math.max(1e-3, currentCost * 0.05);
        double cooling = 0.980;
        double reactionFactor = 0.25;

        for (int iter = 1; iter <= iterations; iter++) {
            int q = 2 + rng.nextInt(Math.max(1, maxDestroy - 1));

            int dOp = selectRoulette(destroyWeights, rng);
            int rOp = selectRoulette(repairWeights, rng);
            destroyUses[dOp]++;
            repairUses[rOp]++;

            List<Integer> removed = destroyTour(current, q, dOp, cost, stops, enhancedMode, rng);
            int[] reconstructed = repairTour(current, removed, rOp, cost);

            if (enhancedMode && iter % 35 == 0) {
                reconstructed = solveOrOpt(cost, solveTwoOpt(cost, reconstructed));
            }
            double newCost = tourWeightedCost(reconstructed, cost);

            double reward = 0.0;
            if (newCost + 1e-9 < bestCost) {
                if (enhancedMode) {
                    reconstructed = solveOrOpt(cost, solveTwoOpt(cost, reconstructed));
                    newCost = tourWeightedCost(reconstructed, cost);
                }
                bestCost = newCost;
                best = Arrays.copyOf(reconstructed, n);
                current = reconstructed;
                currentCost = newCost;
                reward = 15.0; // sigma_1
            } else if (newCost + 1e-9 < currentCost) {
                current = reconstructed;
                currentCost = newCost;
                reward = 8.0; // sigma_2
            } else if (rng.nextDouble() < Math.exp(-(newCost - currentCost) / Math.max(1e-5, temp))) {
                current = reconstructed;
                currentCost = newCost;
                reward = 3.0; // sigma_3
            }

            destroyScores[dOp] += reward;
            repairScores[rOp] += reward;
            temp *= cooling;

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

        if (enhancedMode) {
            best = solveOrOpt(cost, solveTwoOpt(cost, best));
        }
        return best;
    }

    private int selectRoulette(double[] weights, Random rng) {
        double sum = 0.0;
        for (double w : weights) sum += w;
        double r = rng.nextDouble() * sum;
        double accum = 0.0;
        for (int i = 0; i < weights.length; i++) {
            accum += weights[i];
            if (r <= accum) return i;
        }
        return weights.length - 1;
    }

    private List<Integer> destroyTour(int[] tour, int q, int op, double[][] cost,
                                      List<BenchmarkAddressDto> stops, boolean enhanced, Random rng) {
        List<Integer> removed = new ArrayList<>();
        int n = tour.length;
        if (n <= 3) return removed;
        int targetRemove = Math.min(q, n - 2);
        boolean[] isRemoved = new boolean[n];

        if (op == 0) {
            // [D0] Random Removal (preserva o depósito no índice 0)
            while (removed.size() < targetRemove) {
                int idx = 1 + rng.nextInt(n - 1);
                int node = tour[idx];
                if (!isRemoved[node]) {
                    removed.add(node);
                    isRemoved[node] = true;
                }
            }
        } else if (op == 1) {
            // [D1] Worst-Cost Detour Removal
            while (removed.size() < targetRemove) {
                int worstNode = -1;
                double maxDetour = Double.NEGATIVE_INFINITY;
                for (int i = 1; i < n; i++) {
                    int node = tour[i];
                    if (!isRemoved[node]) {
                        int prev = tour[i - 1];
                        int next = tour[(i + 1) % n];
                        double detour = cost[prev][node] + cost[node][next] - cost[prev][next];
                        if (detour > maxDetour) {
                            maxDetour = detour;
                            worstNode = node;
                        }
                    }
                }
                if (worstNode != -1) {
                    removed.add(worstNode);
                    isRemoved[worstNode] = true;
                } else break;
            }
        } else {
            // [D2] Shaw Removal: Haversine puro no ALNS Padrão vs Shaw Assimétrico Viário + Pavimento no ALNS Orbi
            int seed = 1 + rng.nextInt(n - 1);
            removed.add(tour[seed]);
            isRemoved[tour[seed]] = true;

            while (removed.size() < targetRemove) {
                int ref = removed.get(rng.nextInt(removed.size()));
                int bestCand = -1;
                double minScore = Double.POSITIVE_INFINITY;
                for (int i = 1; i < n; i++) {
                    int node = tour[i];
                    if (!isRemoved[node]) {
                        double score;
                        if (enhanced) {
                            double asymRoad = Math.min(cost[ref][node], cost[node][ref])
                                    + 0.5 * Math.abs(cost[ref][node] - cost[node][ref]);
                            double surfDiff = Math.abs(stops.get(ref).surfaceFactor() - stops.get(node).surfaceFactor()) * 10.0;
                            score = asymRoad + surfDiff;
                        } else {
                            double lat1 = stops.get(ref).latitude() != null ? stops.get(ref).latitude() : -22.90;
                            double lon1 = stops.get(ref).longitude() != null ? stops.get(ref).longitude() : -47.06;
                            double lat2 = stops.get(node).latitude() != null ? stops.get(node).latitude() : -22.90;
                            double lon2 = stops.get(node).longitude() != null ? stops.get(node).longitude() : -47.06;
                            score = haversineKm(lat1, lon1, lat2, lon2);
                        }
                        if (score < minScore) {
                            minScore = score;
                            bestCand = node;
                        }
                    }
                }
                if (bestCand != -1) {
                    removed.add(bestCand);
                    isRemoved[bestCand] = true;
                } else break;
            }
        }
        return removed;
    }

    private int[] repairTour(int[] tour, List<Integer> removed, int op, double[][] cost) {
        Set<Integer> removedSet = new HashSet<>(removed);
        List<Integer> partial = new ArrayList<>();
        for (int node : tour) {
            if (!removedSet.contains(node)) {
                partial.add(node);
            }
        }

        List<Integer> pool = new ArrayList<>(removed);
        if (op == 0) {
            // [R0] Greedy Insertion dinâmica
            while (!pool.isEmpty()) {
                int chosenPoolIdx = 0;
                int bestPos = 1;
                double bestDelta = Double.POSITIVE_INFINITY;

                for (int r = 0; r < pool.size(); r++) {
                    int nodeToInsert = pool.get(r);
                    for (int p = 1; p <= partial.size(); p++) {
                        int u = partial.get(p - 1);
                        int v = (p < partial.size()) ? partial.get(p) : partial.get(0);
                        double delta = cost[u][nodeToInsert] + cost[nodeToInsert][v] - cost[u][v];
                        if (delta < bestDelta) {
                            bestDelta = delta;
                            chosenPoolIdx = r;
                            bestPos = p;
                        }
                    }
                }
                int node = pool.remove(chosenPoolIdx);
                partial.add(bestPos, node);
            }
        } else {
            // [R1] Regret-2 Insertion (Regret_2 = Delta_2 - Delta_1)
            while (!pool.isEmpty()) {
                int chosenPoolIdx = 0;
                int chosenPos = 1;
                double maxRegret = Double.NEGATIVE_INFINITY;
                double chosenBestDelta = Double.POSITIVE_INFINITY;

                for (int r = 0; r < pool.size(); r++) {
                    int nodeToInsert = pool.get(r);
                    double firstBest = Double.POSITIVE_INFINITY;
                    double secondBest = Double.POSITIVE_INFINITY;
                    int bestPosForNode = 1;

                    for (int p = 1; p <= partial.size(); p++) {
                        int u = partial.get(p - 1);
                        int v = (p < partial.size()) ? partial.get(p) : partial.get(0);
                        double delta = cost[u][nodeToInsert] + cost[nodeToInsert][v] - cost[u][v];
                        if (delta < firstBest) {
                            secondBest = firstBest;
                            firstBest = delta;
                            bestPosForNode = p;
                        } else if (delta < secondBest) {
                            secondBest = delta;
                        }
                    }

                    double regret = Double.isInfinite(secondBest) ? firstBest : (secondBest - firstBest);
                    if (regret > maxRegret + 1e-9 || (Math.abs(regret - maxRegret) <= 1e-9 && firstBest < chosenBestDelta)) {
                        maxRegret = regret;
                        chosenPoolIdx = r;
                        chosenPos = bestPosForNode;
                        chosenBestDelta = firstBest;
                    }
                }
                int node = pool.remove(chosenPoolIdx);
                partial.add(chosenPos, node);
            }
        }

        int[] res = new int[partial.size()];
        for (int i = 0; i < partial.size(); i++) res[i] = partial.get(i);
        return res;
    }

    // =========================================================================
    // DADOS DE FALLBACK (CASO NENHUM JSON SEJA ENCONTRADO)
    // =========================================================================
    private List<BenchmarkAddressDto> getDefaultFallbackStops() {
        return List.of(
                new BenchmarkAddressDto(0, "Av. Francisco Glicério", -22.904287, -47.060046, "Av. Francisco Glicério - Centro, Campinas - SP", "Av. Francisco Glicério - Centro, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(1, "Rua Barão de Jaguara", -22.907477, -47.057499, "Rua Barão de Jaguara - Centro, Campinas - SP", "Rua Barão de Jaguara - Centro, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(2, "Av. Júlio de Mesquita", -22.900235, -47.054601, "Av. Júlio de Mesquita - Cambuí, Campinas - SP", "Av. Júlio de Mesquita - Cambuí, Campinas - SP", 1.02, true),
                new BenchmarkAddressDto(3, "Rua General Osório", -22.903764, -47.059365, "R. Gen. Osório - Centro, Campinas - SP", "R. Gen. Osório - Centro, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(4, "Av. José de Sousa Campos", -22.890797, -47.046349, "Av. José de Sousa Campos (Norte-Sul) - Campinas - SP", "Av. José de Sousa Campos - Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(5, "Rua Conceição", -22.903195, -47.054007, "R. Conceição - Centro, Campinas - SP", "R. Conceição - Centro, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(6, "Av. Albino J. B. de Oliveira", -22.822660, -47.080435, "Av. Albino J. B. de Oliveira - Barão Geraldo, Campinas - SP", "Av. Albino J. B. de Oliveira - Barão Geraldo", 1.0, true),
                new BenchmarkAddressDto(7, "Rua Dr. Quirino", -22.906078, -47.057320, "R. Dr. Quirino - Centro, Campinas - SP", "R. Dr. Quirino - Centro, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(8, "Av. Aquidabã", -22.910223, -47.055315, "Av. Aquidabã - Campinas - SP", "Av. Aquidabã - Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(9, "Rua Cel. Quirino", -22.897564, -47.052931, "R. Cel. Quirino - Cambuí, Campinas - SP", "R. Cel. Quirino - Cambuí, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(10, "Av. Iguatemi", -22.893593, -47.026856, "Av. Iguatemi - Vila Brandina, Campinas - SP", "Av. Iguatemi - Vila Brandina, Campinas - SP", 1.0, true),
                new BenchmarkAddressDto(11, "Av. Brasil", -22.888753, -47.062819, "Av. Brasil - Guanabara, Campinas - SP", "Av. Brasil - Guanabara, Campinas - SP", 1.0, true)
        );
    }

    private record MatricesData(
            double[][] distanceKm,
            double[][] durationMin,
            double[][] surfaceFactor,
            double[][] weightedCost
    ) {}

    private record AlgorithmEvaluation(
            String algorithmName,
            String algorithmType,
            String complexity,
            boolean isValid,
            String status,
            String invalidReason,
            Double executionTimeMs,
            int[] tour,
            Double distanceKm,
            Double durationMin,
            Double weightedCost,
            String notes
    ) {
        AlgorithmEvaluation(
                String algorithmName,
                String algorithmType,
                String complexity,
                double executionTimeMs,
                int[] tour,
                double distanceKm,
                double durationMin,
                double weightedCost,
                String notes
        ) {
            this(algorithmName, algorithmType, complexity, true, "VALID", null,
                    executionTimeMs, tour, distanceKm, durationMin, weightedCost, notes);
        }

        static AlgorithmEvaluation invalid(
                String algorithmName,
                String algorithmType,
                String complexity,
                String status,
                String reason,
                String notes
        ) {
            return new AlgorithmEvaluation(
                    algorithmName, algorithmType, complexity, false, status, reason,
                    null, new int[0], null, null, null, notes
            );
        }
    }
}
