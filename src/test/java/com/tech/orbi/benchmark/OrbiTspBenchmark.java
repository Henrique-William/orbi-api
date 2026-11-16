package com.tech.orbi.benchmark;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Bateria Científica Multi-Cenários Calibrada pelas Médias Reais do Mercado Logístico (IC - Ecossistema Orbi).
 *
 * Faixas Reais de Paradas Diárias por Motorista na Logística & Delivery (Brasil e Global):
 *  - C1: Carga Pesada / Móveis / B2B Doca (N = 12 paradas/dia | Faixa real: 10 a 25 paradas/dia)
 *  - C2: Barreira Geográfica / Logística Portuária Santos x Guarujá (N = 15 paradas/dia | Contorno Estuarino via Cubatão)
 *  - C3: Logística Rural e Estradas Não Pavimentadas - Joaquim Egídio/Sousas (N = 18 paradas/dia | Limite dos Métodos Exatos, Phi_ij até 1.28)
 *  - C4: Distribuição Comercial B2B / VUC Regional RMC (N = 35 paradas/dia | Faixa real B2B/Intermunicipal: 30 a 50 paradas/dia)
 *  - C5: E-commerce Last-Mile Padrão Brasil - Vans Mercado Livre/Shopee (N = 70 paradas/dia | Faixa real B2C Brasil: 60 a 90 paradas/dia)
 *  - C6: E-commerce Alta Densidade / Pico Black Friday - Amazon DSP (N = 110 paradas/dia | Faixa real High-Density: 100 a 150+ paradas/dia)
 */
public class OrbiTspBenchmark {

    private static final String DEFAULT_GOOGLE_API_KEY = "";
    private static final String DEFAULT_ORS_API_KEY = "";

    private static final String ORS_BASE_URL = "https://api.openrouteservice.org";
    private static final String GOOGLE_GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    private static final int MAX_SAFE_GOOGLE_GEOCODE_REQUESTS = 35;
    private static final int MAX_SAFE_ORS_REQUESTS_PER_RUN = 25;
    private static final long MAX_BRANCH_AND_BOUND_NODES = 3_000_000L;
    private static final int MAX_HELD_KARP_EXACT_N = 18; // Limite seguro em RAM: 18 * 2^17 = 2.35M estados (~40 MB). Acima disso explode exponencialmente!

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private int googleRequestsCount = 0;
    private int orsMatrixRequestsCount = 0;
    private int orsDirectionsRequestsCount = 0;
    private final Map<String, Double> orsRouteSurfaceCache = new HashMap<>();

    private static String configuredExternalJsonPath = null;

    public static void setExternalJsonPath(String path) {
        configuredExternalJsonPath = path;
    }

    public static void main(String[] args) {
        String externalJson = (args != null && args.length > 0) ? args[0] : null;
        new OrbiTspBenchmark().executeFullBenchmark(externalJson);
    }

    public void executeFullBenchmark() {
        executeFullBenchmark(null);
    }

    public void executeFullBenchmark(String externalJsonPath) {
        if (externalJsonPath != null && !externalJsonPath.isBlank()) {
            configuredExternalJsonPath = externalJsonPath;
        }
        printBanner();

        String googleApiKey = resolveKey("GOOGLE_API_KEY", DEFAULT_GOOGLE_API_KEY);
        String orsApiKey = resolveKey("ORS_API_KEY", DEFAULT_ORS_API_KEY);

        List<StopPoint> campinasPool110Default = getCampinasMasterPool110();
        List<StopPoint> santosGuaruja15Default = getSantosGuarujaPool15();

        if (!checkQuotaSafety(campinasPool110Default, santosGuaruja15Default, !googleApiKey.isBlank(), !orsApiKey.isBlank())) {
            return;
        }

        // 1. Geocodificação via Google Maps API (apenas para pontos ainda não em cache homologado)
        List<StopPoint> campinasPool110 = resolveCoordinatesWithGoogleApi(campinasPool110Default, googleApiKey);
        List<StopPoint> santosPool15 = resolveCoordinatesWithGoogleApi(santosGuaruja15Default, googleApiKey);

        printMarketResearchHeader();

        // 2. Obtenção das Matrizes Viárias Base:
        //    - 1 matriz 110x110 real para Campinas/Rural/RMC/Last-Mile
        //    - 1 matriz 15x15 real para Santos/Guarujá/Cubatão/São Vicente!
        MatricesData masterCampinas110 = buildRoadMatrices(campinasPool110, orsApiKey, true);
        MatricesData masterSantos15 = buildRoadMatrices(santosPool15, orsApiKey, false);

        List<ScenarioSummary> batterySummaries = new ArrayList<>();

        // =========================================================================
        // CENÁRIO 1: CARGA PESADA / MÓVEIS / B2B DOCA (N = 12 paradas/dia)
        // =========================================================================
        int[] c1Idx = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
        batterySummaries.add(runScenarioBenchmark(
                "C1: CARGA PESADA / B2B DOCA (N = 12 paradas/dia | Campinas)",
                "Faixa de Mercado: 10 a 25 paradas/dia (Entregas volumosas/linha branca c/ ~18 min de doca por cliente).",
                sliceStops(campinasPool110, c1Idx),
                sliceMatricesByIndices(masterCampinas110, c1Idx),
                18.0, // dwell time médio por parada (min)
                orsApiKey,
                true,
                false
        ));

        // =========================================================================
        // CENÁRIO 2: BARREIRA GEOGRÁFICA / LOGÍSTICA PORTUÁRIA (N = 15 paradas/dia - Santos x Guarujá)
        // =========================================================================
        int[] c2Idx = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 };
        batterySummaries.add(runScenarioBenchmark(
                "C2: BARREIRA GEOGRÁFICA SANTOS x GUARUJÁ (N = 15 paradas/dia | Baixada Santista)",
                "Faixa de Mercado: 15 a 25 paradas/dia (Cargas portuárias/comerciais onde 450m no canal = 52 km por rodovia).",
                sliceStops(santosPool15, c2Idx),
                sliceMatricesByIndices(masterSantos15, c2Idx),
                12.0,
                orsApiKey,
                false,
                true
        ));

        // =========================================================================
        // CENÁRIO 3: LOGÍSTICA RURAL E VIAS NÃO PAVIMENTADAS (N = 18 paradas/dia - Joaquim Egídio/Sousas)
        // =========================================================================
        int[] c3Idx = { 0, 2, 4, 6, 9, 10, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25 };
        batterySummaries.add(runScenarioBenchmark(
                "C3: LOGÍSTICA RURAL E ESTRADAS DE TERRA (N = 18 paradas/dia | Joaquim Egídio/Sousas)",
                "Faixa de Mercado: 15 a 30 paradas/dia em zona rural (Fronteira máxima dos Métodos Exatos; Phi_ij até 1.28).",
                sliceStops(campinasPool110, c3Idx),
                sliceMatricesByIndices(masterCampinas110, c3Idx),
                10.0,
                orsApiKey,
                false,
                false
        ));

        // =========================================================================
        // CENÁRIO 4: DISTRIBUIÇÃO COMERCIAL B2B / VUC REGIONAL (N = 35 paradas/dia - RMC)
        // =========================================================================
        int[] c4Idx = new int[35];
        for (int i = 0; i < 35; i++) c4Idx[i] = i;
        batterySummaries.add(runScenarioBenchmark(
                "C4: DISTRIBUIÇÃO B2B / VUC REGIONAL (N = 35 paradas/dia | Campinas + RMC)",
                "Faixa de Mercado: 30 a 50 paradas/dia (Abastecimento B2B de lojas/farmácias; Exatos já colapsam em 7.2 TB RAM!).",
                sliceStops(campinasPool110, c4Idx),
                sliceMatricesByIndices(masterCampinas110, c4Idx),
                7.0,
                orsApiKey,
                false,
                false
        ));

        // =========================================================================
        // CENÁRIO 5: E-COMMERCE LAST-MILE PADRÃO BRASIL - VANS/UTILITÁRIOS (N = 70 paradas/dia REAIS)
        // =========================================================================
        int[] c5Idx = new int[70];
        for (int i = 0; i < 70; i++) c5Idx[i] = i;
        batterySummaries.add(runScenarioBenchmark(
                "C5: E-COMMERCE LAST-MILE PADRÃO BRASIL (N = 70 paradas/dia | Vans Mercado Livre/Shopee)",
                "Faixa de Mercado: 60 a 90 paradas/dia (Média nacional de vans B2C no Brasil; ~4 min de estacionamento/entrega).",
                sliceStops(campinasPool110, c5Idx),
                sliceMatricesByIndices(masterCampinas110, c5Idx),
                4.0,
                "",
                false,
                false
        ));

        // =========================================================================
        // CENÁRIO 6: E-COMMERCE ALTA DENSIDADE / PICO BLACK FRIDAY (N = 110 paradas/dia REAIS)
        // =========================================================================
        int[] c6Idx = new int[110];
        for (int i = 0; i < 110; i++) c6Idx[i] = i;
        batterySummaries.add(runScenarioBenchmark(
                "C6: E-COMMERCE ALTA DENSIDADE / BLACK FRIDAY (N = 110 paradas/dia | Padrão Amazon DSP)",
                "Faixa de Mercado: 100 a 150+ paradas/dia (Rota ultra-densa B2C: 1.33 x 10^176 rotas; teste extremo de CPU!).",
                sliceStops(campinasPool110, c6Idx),
                sliceMatricesByIndices(masterCampinas110, c6Idx),
                3.0,
                "",
                false,
                false
        ));

        printConsolidatedBatteryReport(batterySummaries);
        printApiConsumptionReport();
    }

    // =========================================================================
    // EXECUÇÃO DE UM CENÁRIO COMPLETO + DIAGNÓSTICO DO ALNS E TURNO LOGÍSTICO
    // =========================================================================
    private ScenarioSummary runScenarioBenchmark(
            String scenarioTitle,
            String scenarioDescription,
            List<StopPoint> stops,
            MatricesData matrices,
            double dwellTimePerStopMin,
            String orsApiKey,
            boolean fetchLiveSurfaceForWinners,
            boolean isGeographicBarrierScenario) {
        int n = stops.size();
        System.out.println("\n==========================================================================================================================================");
        System.out.println("  " + scenarioTitle);
        System.out.println("  " + scenarioDescription);
        if (n <= 20) {
            System.out.printf(Locale.US, "  Espaço Assimétrico Real (N-1)! = %,d rotas | Estados DP Held-Karp (N * 2^(N-1)) = %,d%n",
                    factorial(n - 1), (long) n * (1L << (n - 1)));
        } else {
            double ramGb = ((double) n * Math.pow(2.0, n - 1) * 12.0) / (1024.0 * 1024.0 * 1024.0);
            System.out.printf(Locale.US, "  Espaço Assimétrico Real (N-1)! = %.3e rotas | Memória Exigida pelo Held-Karp DP = %.2e GB RAM (INVIÁVEL!)%n",
                    factorialDouble(n - 1), ramGb);
        }
        System.out.println("==========================================================================================================================================");

        int[] baselineTour = createBaselineTour(n);
        RouteMetrics baseline = evaluateTour(
                "0. Baseline (Ordem de Cadastro)",
                "O(n)",
                "Baseline",
                baselineTour,
                0.0,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                "Sequência empírica sem otimização"
        );

        List<RouteMetrics> results = new ArrayList<>();

        // --- GRUPO A: MÉTODOS EXATOS ---
        // 1. Branch-and-Bound (DFS)
        ExactResult bbRes = benchmarkBranchAndBound(matrices.weightedCostMatrix, 0, n <= 11 ? 5 : 1);
        results.add(evaluateTour(
                "1. Branch-and-Bound (DFS)",
                "O((n-1)!)",
                "Exato",
                bbRes.tour,
                bbRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                bbRes.truncated
                        ? String.format(Locale.US, "Truncado em %,d nós (Explosão Fatorial!)", bbRes.nodesVisited)
                        : String.format(Locale.US, "Ótimo Exato (%,d nós | %,d podas)", bbRes.nodesVisited, bbRes.branchesPruned)
        ));

        // 2. Bellman-Held-Karp (Programação Dinâmica Exata)
        if (n <= MAX_HELD_KARP_EXACT_N) {
            AlgoResult hkRes = benchmarkHeldKarpExactDp(matrices.weightedCostMatrix, 0, n <= 12 ? 10 : 1);
            results.add(evaluateTour(
                    "2. Bellman-Held-Karp (Prog. Dinâmica)",
                    "O(n^2 * 2^n)",
                    "Exato (Estado da Arte)",
                    hkRes.tour,
                    hkRes.avgTimeMs,
                    matrices,
                    stops,
                    fetchLiveSurfaceForWinners ? orsApiKey : "",
                    String.format(Locale.US, "Ótimo Global 100%% Garantido (%,d estados DP)", (1L << (n - 1)) * n)
            ));
        } else {
            double ramGb = ((double) n * Math.pow(2.0, n - 1) * 12.0) / (1024.0 * 1024.0 * 1024.0);
            results.add(evaluateTour(
                    "2. Bellman-Held-Karp (Prog. Dinâmica)",
                    "O(n^2 * 2^n)",
                    "Exato (Out-Of-Memory)",
                    bbRes.tour,
                    0.0,
                    matrices,
                    stops,
                    "",
                    String.format(Locale.US, "COLAPSO DE MEMÓRIA EM N=%d: Exigiria %.1e GB RAM!", n, ramGb)
            ));
        }

        // --- GRUPO B: MÉTODOS HEURÍSTICOS ---
        // 3. Vizinho Mais Próximo com Poda LB (orbi-api atual)
        NnResult nnRes = benchmarkNearestNeighborWithPruning(matrices.distanceKmMatrix, matrices.surfaceFactorMatrix, 0);
        results.add(evaluateTour(
                "3. Vizinho Mais Próximo (NN / orbi-api)",
                "Theta(n^2)",
                "Heurística Construtiva",
                nnRes.tour,
                nnRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                String.format(Locale.US, "Poda LB cortou %d/%d chamadas (%.1f%%)",
                        nnRes.prunedEvaluations, nnRes.totalPossibleEvaluations,
                        (100.0 * nnRes.prunedEvaluations) / Math.max(1, nnRes.totalPossibleEvaluations))
        ));

        // 4. Híbrido NN + 2-opt
        AlgoResult nnTwoOptRes = benchmarkNnPlusTwoOpt(matrices.distanceKmMatrix, matrices.surfaceFactorMatrix, matrices.weightedCostMatrix, 0);
        results.add(evaluateTour(
                "4. Híbrido NN + 2-opt",
                "O(k * n^2)",
                "Heurística Híbrida",
                nnTwoOptRes.tour,
                nnTwoOptRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                "Combina rapidez do NN com desentrelaçamento 2-opt"
        ));

        // 5. 3-opt / Lin-Kernighan Simplificado (LKH)
        AlgoResult lkhRes = benchmarkThreeOptLkh(matrices.weightedCostMatrix, nnRes.tour);
        results.add(evaluateTour(
                "5. 3-opt / Lin-Kernighan (LKH)",
                "O(k * n^3)",
                "Heurística Estado da Arte",
                lkhRes.tour,
                lkhRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                "Realocação direcional de segmentos sem inversão (k-opt)"
        ));

        // --- GRUPO C: MÉTODOS META-HEURÍSTICOS ---
        // 6. Simulated Annealing (SA)
        AlgoResult saRes = benchmarkSimulatedAnnealing(matrices.weightedCostMatrix, 0);
        results.add(evaluateTour(
                "6. Simulated Annealing (SA)",
                "O(K * L)",
                "Meta-heurística Trajetória",
                saRes.tour,
                saRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                "Resfriamento geométrico c/ vizinhança 2-opt + Or-opt"
        ));

        // 7. Algoritmo Memético (GA + Busca Local 2-opt)
        AlgoResult memeticRes = benchmarkGeneticAlgorithm(matrices.weightedCostMatrix, 0, true);
        results.add(evaluateTour(
                "7. Algoritmo Memético (GA + 2-opt)",
                "O(G * P * n^2)",
                "Meta-heurística Populacional",
                memeticRes.tour,
                memeticRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                "Evolução genética + refinamento Lamarckiano 2-opt"
        ));

        // 8. ALNS Padrão (Adaptive Large Neighborhood Search - Ropke & Pisinger)
        AlnsDetailedResult alnsStdRes = benchmarkAlnsDetailed(matrices.weightedCostMatrix, stops, 0, false);
        results.add(evaluateTour(
                "8. ALNS Padrão (Ropke & Pisinger)",
                "O(I * q * n^2)",
                "Meta-heurística (Foco IC)",
                alnsStdRes.bestTour,
                alnsStdRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                String.format(Locale.US, "Média seeds: %.2f | Pior: %.2f | DP: %.2f",
                        alnsStdRes.meanCost, alnsStdRes.worstCost, alnsStdRes.stdDevCost)
        ));

        // 9. ALNS Aperfeiçoado (Proposta de Melhoria da IC: Shaw Assimétrico Viário + Intensificação Local)
        AlnsDetailedResult alnsEnhRes = benchmarkAlnsDetailed(matrices.weightedCostMatrix, stops, 0, true);
        results.add(evaluateTour(
                "9. ALNS Aperfeiçoado (Shaw Viário+LS)",
                "O(I * q * n^2)",
                "Meta-heurística (Melhorado)",
                alnsEnhRes.bestTour,
                alnsEnhRes.avgTimeMs,
                matrices,
                stops,
                fetchLiveSurfaceForWinners ? orsApiKey : "",
                String.format(Locale.US, "Média seeds: %.2f | Pior: %.2f | DP: %.2f",
                        alnsEnhRes.meanCost, alnsEnhRes.worstCost, alnsEnhRes.stdDevCost)
        ));

        double referenceBestCost = results.stream()
                .mapToDouble(r -> r.weightedCost)
                .min()
                .orElse(baseline.weightedCost);

        printScenarioComparisonTable(baseline, results, referenceBestCost);
        printAlnsDiagnosticBox(stops, matrices, baseline, alnsStdRes, alnsEnhRes, dwellTimePerStopMin, isGeographicBarrierScenario);

        RouteMetrics winnerCost = results.stream()
                .min(Comparator.comparingDouble((RouteMetrics r) -> Math.round(r.weightedCost * 100.0) / 100.0)
                        .thenComparingDouble(r -> r.executionTimeMs))
                .orElse(results.get(0));

        RouteMetrics winnerFastest = results.stream()
                .filter(r -> r.executionTimeMs > 0.0)
                .min(Comparator.comparingDouble(r -> r.executionTimeMs))
                .orElse(results.get(2));

        double nnGap = ((results.get(2).weightedCost - referenceBestCost) / referenceBestCost) * 100.0;
        double twoOptGap = ((results.get(3).weightedCost - referenceBestCost) / referenceBestCost) * 100.0;
        double lkhGap = ((results.get(4).weightedCost - referenceBestCost) / referenceBestCost) * 100.0;
        double alnsStdGap = ((results.get(7).weightedCost - referenceBestCost) / referenceBestCost) * 100.0;
        double alnsEnhGap = ((results.get(8).weightedCost - referenceBestCost) / referenceBestCost) * 100.0;

        return new ScenarioSummary(
                scenarioTitle,
                n,
                baseline.distanceKm,
                baseline.durationMin,
                referenceBestCost,
                winnerCost.algorithmName,
                winnerCost.distanceKm,
                winnerCost.durationMin,
                winnerCost.executionTimeMs,
                nnGap,
                twoOptGap,
                lkhGap,
                alnsStdGap,
                alnsEnhGap,
                alnsStdRes.avgTimeMs,
                alnsEnhRes.avgTimeMs,
                results.get(4).executionTimeMs, // 3-opt CPU
                results.get(6).executionTimeMs, // Memetic CPU
                winnerFastest.algorithmName,
                winnerFastest.executionTimeMs
        );
    }

    // =========================================================================
    // CONSTRUTOR DE INSTÂNCIAS DE ALTA DENSIDADE LAST-MILE (N = 70 e N = 110)
    // =========================================================================
    private EcommerceClusterInstance buildClusteredLastMileInstance(
            List<StopPoint> basePool35,
            MatricesData baseMatrices35,
            int targetN,
            long seed) {
        Random rng = new Random(seed);
        List<StopPoint> expandedStops = new ArrayList<>(targetN);
        int[] parentHub = new int[targetN];
        double[] localOffsetLat = new double[targetN];
        double[] localOffsetLng = new double[targetN];

        // Distribui as targetN entregas ao redor dos 35 hubs reais de Campinas (simulando 2 a 4 pacotes por bairro/quadra)
        // mas na ordem de cadastro os pacotes chegam embaralhados na triagem do galpão (como ocorre no e-commerce!)
        int[] shuffledHubOrder = new int[targetN];
        for (int i = 0; i < targetN; i++) {
            shuffledHubOrder[i] = (i * 11) % basePool35.size();
        }
        shuffledHubOrder[0] = 0; // Origem fixa no CD Central

        for (int i = 0; i < targetN; i++) {
            int hubIdx = shuffledHubOrder[i];
            StopPoint hub = basePool35.get(hubIdx);
            parentHub[i] = hubIdx;
            double dLat = (i < basePool35.size()) ? 0.0 : (rng.nextDouble() - 0.5) * 0.0065; // ~350m de raio na mesma zona
            double dLng = (i < basePool35.size()) ? 0.0 : (rng.nextDouble() - 0.5) * 0.0065;
            localOffsetLat[i] = dLat;
            localOffsetLng[i] = dLng;
            expandedStops.add(new StopPoint(
                    i,
                    hub.address + " (Entrega #" + (i + 1) + ")",
                    hub.latitude + dLat,
                    hub.longitude + dLng,
                    hub.formattedAddress,
                    hub.characteristicSurfaceFactor,
                    true
            ));
        }

        double[][] dist = new double[targetN][targetN];
        double[][] dur = new double[targetN][targetN];
        double[][] surf = new double[targetN][targetN];
        double[][] weight = new double[targetN][targetN];

        for (int i = 0; i < targetN; i++) {
            for (int j = 0; j < targetN; j++) {
                if (i == j) {
                    surf[i][j] = 1.0;
                    continue;
                }
                int hI = parentHub[i];
                int hJ = parentHub[j];
                double microKm = haversineKm(
                        expandedStops.get(i).latitude, expandedStops.get(i).longitude,
                        expandedStops.get(j).latitude, expandedStops.get(j).longitude
                );
                if (hI == hJ) {
                    // Deslocamento intra-bairro/quadra (com assimetria de mão única local)
                    double localAsym = (i < j) ? 1.28 : 1.42;
                    dist[i][j] = Math.max(0.18, microKm * localAsym);
                    dur[i][j] = (dist[i][j] / 22.0) * 60.0 + 0.6;
                    surf[i][j] = expandedStops.get(i).characteristicSurfaceFactor;
                } else {
                    // Deslocamento entre bairros herdado diretamente da matriz real 35x35 do OpenRouteService!
                    double intraAdjustment = (rng.nextDouble() - 0.3) * 0.35;
                    dist[i][j] = Math.max(0.40, baseMatrices35.distanceKmMatrix[hI][hJ] + intraAdjustment);
                    dur[i][j] = Math.max(0.80, baseMatrices35.durationMinMatrix[hI][hJ] + intraAdjustment * 1.6);
                    surf[i][j] = baseMatrices35.surfaceFactorMatrix[hI][hJ];
                }
                weight[i][j] = dist[i][j] * surf[i][j];
            }
        }
        return new EcommerceClusterInstance(expandedStops, new MatricesData(dist, dur, surf, weight));
    }

    // =========================================================================
    // IMPLEMENTAÇÃO DETALHADA DO ALNS (PADRÃO vs APERFEIÇOADO)
    // =========================================================================
    private AlnsDetailedResult benchmarkAlnsDetailed(
            double[][] costMatrix,
            List<StopPoint> stops,
            int startNode,
            boolean enhancedMode) {
        int n = costMatrix.length;
        solveAlnsSingleRun(costMatrix, stops, startNode, 77L, enhancedMode);

        int runs = (n <= 20) ? 12 : (n <= 40 ? 6 : 4);
        long startNs = System.nanoTime();
        int[] bestTour = null;
        double bestCost = Double.POSITIVE_INFINITY;
        double worstCost = Double.NEGATIVE_INFINITY;
        double sumCost = 0.0;
        double[] allCosts = new double[runs];
        double[] avgDestroyWeights = new double[3];
        double[] avgRepairWeights = new double[2];

        for (int r = 0; r < runs; r++) {
            AlnsRunTrace trace = solveAlnsSingleRun(costMatrix, stops, startNode, 9000L + r * 31L, enhancedMode);
            double cost = trace.bestCost;
            allCosts[r] = cost;
            sumCost += cost;
            if (cost < bestCost) {
                bestCost = cost;
                bestTour = trace.bestTour;
            }
            if (cost > worstCost) {
                worstCost = cost;
            }
            for (int i = 0; i < 3; i++) avgDestroyWeights[i] += trace.finalDestroyWeights[i] / runs;
            for (int i = 0; i < 2; i++) avgRepairWeights[i] += trace.finalRepairWeights[i] / runs;
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        double meanCost = sumCost / runs;
        double variance = 0.0;
        for (double c : allCosts) variance += (c - meanCost) * (c - meanCost);
        double stdDev = Math.sqrt(variance / runs);

        return new AlnsDetailedResult(bestTour, avgMs, bestCost, meanCost, worstCost, stdDev, avgDestroyWeights, avgRepairWeights);
    }

    private AlnsRunTrace solveAlnsSingleRun(
            double[][] costMatrix,
            List<StopPoint> stops,
            int startNode,
            long seed,
            boolean enhancedMode) {
        int n = costMatrix.length;
        Random rng = new Random(seed);

        NnResult initNn = solveNearestNeighborWithPruning(costMatrix, new double[n][n], startNode, false);
        int[] currentTour = enhancedMode ? solveTwoOptFast(costMatrix, initNn.tour) : initNn.tour.clone();
        double currentCost = tourCost(currentTour, costMatrix);

        int[] bestTour = currentTour.clone();
        double bestCost = currentCost;

        double[] destroyWeights = { 1.0, 1.0, 1.0 }; // [D0] Random, [D1] Worst-Cost, [D2] Shaw Removal
        double[] repairWeights  = { 1.0, 1.0 };      // [R0] Greedy Insertion, [R1] 2-Regret Insertion
        double[] destroyScores  = new double[3];
        double[] repairScores   = new double[2];
        int[] destroyUses       = new int[3];
        int[] repairUses        = new int[2];

        int maxIterations = enhancedMode ? (n <= 40 ? 180 : 120) : (n <= 40 ? 220 : 140);
        int maxQ = enhancedMode ? Math.max(2, Math.min(7, (int) Math.round(n * 0.22)))
                                : Math.max(2, Math.min(12, (int) Math.round(n * 0.30)));
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

            applyAlnsDestroyMode(partialRoute, removedPool, q, dOp, costMatrix, stops, rng, enhancedMode);
            applyAlnsRepair(partialRoute, removedPool, rOp, costMatrix);

            int[] candidateTour = new int[n + 1];
            for (int i = 0; i <= n; i++) candidateTour[i] = partialRoute.get(i);

            if (enhancedMode && iter % 35 == 0) {
                candidateTour = solveOrOptFast(costMatrix, solveTwoOptFast(costMatrix, candidateTour));
            }
            double candidateCost = tourCost(candidateTour, costMatrix);

            double reward = 0.0;
            if (candidateCost + 1e-9 < bestCost) {
                if (enhancedMode) {
                    candidateTour = solveOrOptFast(costMatrix, solveTwoOptFast(costMatrix, candidateTour));
                    candidateCost = tourCost(candidateTour, costMatrix);
                }
                bestCost = candidateCost;
                bestTour = candidateTour.clone();
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 15.0;
            } else if (candidateCost + 1e-9 < currentCost) {
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 8.0;
            } else if (rng.nextDouble() < Math.exp(-(candidateCost - currentCost) / Math.max(1e-5, temp))) {
                currentTour = candidateTour;
                currentCost = candidateCost;
                reward = 3.0;
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
        return new AlnsRunTrace(bestTour, bestCost, destroyWeights, repairWeights);
    }

    private void applyAlnsDestroyMode(
            List<Integer> route,
            List<Integer> removedPool,
            int q,
            int dOp,
            double[][] costMatrix,
            List<StopPoint> stops,
            Random rng,
            boolean useAsymmetricRoadShaw) {
        if (dOp == 0) {
            for (int c = 0; c < q && route.size() > 3; c++) {
                int idx = 1 + rng.nextInt(route.size() - 2);
                removedPool.add(route.remove(idx));
            }
        } else if (dOp == 1) {
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
            int seedIdx = 1 + rng.nextInt(route.size() - 2);
            int seedNode = route.remove(seedIdx);
            removedPool.add(seedNode);

            while (removedPool.size() < q && route.size() > 3) {
                int closestIdx = 1;
                double minRelatedness = Double.POSITIVE_INFINITY;
                for (int idx = 1; idx < route.size() - 1; idx++) {
                    int cand = route.get(idx);
                    double relatedness;
                    if (useAsymmetricRoadShaw) {
                        relatedness = Math.min(costMatrix[seedNode][cand], costMatrix[cand][seedNode])
                                + 0.5 * Math.abs(costMatrix[seedNode][cand] - costMatrix[cand][seedNode]);
                    } else {
                        relatedness = haversineKm(
                                stops.get(seedNode).latitude, stops.get(seedNode).longitude,
                                stops.get(cand).latitude, stops.get(cand).longitude
                        );
                    }
                    if (relatedness < minRelatedness) {
                        minRelatedness = relatedness;
                        closestIdx = idx;
                    }
                }
                removedPool.add(route.remove(closestIdx));
            }
        }
    }

    // =========================================================================
    // DEMAIS ALGORITMOS DO BENCHMARK (EXATOS, HEURÍSTICAS E META-HEURÍSTICAS)
    // =========================================================================
    private ExactResult benchmarkBranchAndBound(double[][] costMatrix, int startNode, int runs) {
        if (runs > 1) {
            solveBranchAndBound(costMatrix, startNode);
        }
        long startNs = System.nanoTime();
        ExactResult res = null;
        for (int r = 0; r < runs; r++) {
            res = solveBranchAndBound(costMatrix, startNode);
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new ExactResult(res.tour, avgMs, res.nodesVisited, res.branchesPruned, res.truncated);
    }

    private ExactResult solveBranchAndBound(double[][] costMatrix, int startNode) {
        int n = costMatrix.length;
        boolean[] visited = new boolean[n];
        int[] currentPath = new int[n + 1];
        int[] bestTour = new int[n + 1];

        NnResult initialBound = solveNearestNeighborWithPruning(costMatrix, new double[n][n], startNode, false);
        double[] bestCost = { tourCost(initialBound.tour, costMatrix) };
        System.arraycopy(initialBound.tour, 0, bestTour, 0, n + 1);

        visited[startNode] = true;
        currentPath[0] = startNode;
        long[] counters = new long[3];

        branchAndBoundDfs(costMatrix, visited, currentPath, 1, 0.0, startNode, bestCost, bestTour, counters);
        return new ExactResult(bestTour, 0.0, counters[0], counters[1], counters[2] == 1L);
    }

    private void branchAndBoundDfs(
            double[][] costMatrix,
            boolean[] visited,
            int[] currentPath,
            int level,
            double currentCost,
            int startNode,
            double[] bestCost,
            int[] bestTour,
            long[] counters) {
        if (counters[0] >= MAX_BRANCH_AND_BOUND_NODES) {
            counters[2] = 1L;
            return;
        }
        int n = costMatrix.length;
        int lastNode = currentPath[level - 1];

        if (level == n) {
            double totalCost = currentCost + costMatrix[lastNode][startNode];
            if (totalCost < bestCost[0] - 1e-9) {
                bestCost[0] = totalCost;
                System.arraycopy(currentPath, 0, bestTour, 0, n);
                bestTour[n] = startNode;
            }
            return;
        }

        for (int next = 0; next < n; next++) {
            if (!visited[next]) {
                counters[0]++;
                double nextCost = currentCost + costMatrix[lastNode][next];
                if (nextCost >= bestCost[0] - 1e-9) {
                    counters[1]++;
                    continue;
                }
                visited[next] = true;
                currentPath[level] = next;
                branchAndBoundDfs(costMatrix, visited, currentPath, level + 1, nextCost, startNode, bestCost, bestTour, counters);
                visited[next] = false;
                if (counters[2] == 1L) return;
            }
        }
    }

    private AlgoResult benchmarkHeldKarpExactDp(double[][] costMatrix, int startNode, int runs) {
        if (runs > 1) {
            solveHeldKarpExactDp(costMatrix);
        }
        long startNs = System.nanoTime();
        int[] tour = null;
        for (int r = 0; r < runs; r++) {
            tour = solveHeldKarpExactDp(costMatrix);
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new AlgoResult(tour, avgMs);
    }

    private int[] solveHeldKarpExactDp(double[][] costMatrix) {
        int n = costMatrix.length;
        int numSubsets = 1 << (n - 1);
        double[][] dp = new double[numSubsets][n];
        int[][] parent = new int[numSubsets][n];

        for (int mask = 0; mask < numSubsets; mask++) {
            Arrays.fill(dp[mask], Double.POSITIVE_INFINITY);
        }

        for (int i = 1; i < n; i++) {
            int bit = 1 << (i - 1);
            dp[bit][i] = costMatrix[0][i];
            parent[bit][i] = 0;
        }

        for (int mask = 1; mask < numSubsets; mask++) {
            for (int last = 1; last < n; last++) {
                if ((mask & (1 << (last - 1))) == 0) continue;
                double prevCost = dp[mask][last];
                if (prevCost == Double.POSITIVE_INFINITY) continue;

                for (int next = 1; next < n; next++) {
                    int nextBit = 1 << (next - 1);
                    if ((mask & nextBit) != 0) continue;
                    int nextMask = mask | nextBit;
                    double candCost = prevCost + costMatrix[last][next];
                    if (candCost < dp[nextMask][next]) {
                        dp[nextMask][next] = candCost;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }

        int fullMask = numSubsets - 1;
        double bestCost = Double.POSITIVE_INFINITY;
        int lastNode = -1;
        for (int i = 1; i < n; i++) {
            double total = dp[fullMask][i] + costMatrix[i][0];
            if (total < bestCost) {
                bestCost = total;
                lastNode = i;
            }
        }

        int[] tour = new int[n + 1];
        tour[0] = 0;
        tour[n] = 0;
        int currMask = fullMask;
        int currNode = lastNode;
        for (int idx = n - 1; idx >= 1; idx--) {
            tour[idx] = currNode;
            int prevNode = parent[currMask][currNode];
            currMask ^= (1 << (currNode - 1));
            currNode = prevNode;
        }
        return tour;
    }

    private NnResult benchmarkNearestNeighborWithPruning(double[][] distanceMatrix, double[][] surfaceMatrix, int startNode) {
        for (int w = 0; w < 20; w++) {
            solveNearestNeighborWithPruning(distanceMatrix, surfaceMatrix, startNode, true);
        }
        long startNs = System.nanoTime();
        NnResult result = null;
        int runs = 80;
        for (int r = 0; r < runs; r++) {
            result = solveNearestNeighborWithPruning(distanceMatrix, surfaceMatrix, startNode, true);
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new NnResult(result.tour, avgMs, result.prunedEvaluations, result.totalPossibleEvaluations);
    }

    private NnResult solveNearestNeighborWithPruning(double[][] distances, double[][] surfaceFactors, int startIndex, boolean useSurface) {
        int n = distances.length;
        int[] tour = new int[n + 1];
        boolean[] visited = new boolean[n];
        int currentIndex = startIndex;

        int prunedCount = 0;
        int totalCandidatesCount = 0;

        for (int step = 0; step < n; step++) {
            tour[step] = currentIndex;
            visited[currentIndex] = true;

            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!visited[i]) candidates.add(i);
            }
            if (candidates.isEmpty()) break;

            totalCandidatesCount += candidates.size();
            final int from = currentIndex;
            candidates.sort(Comparator.comparingDouble(c -> distances[from][c]));

            int bestCandidate = -1;
            double bestScore = Double.POSITIVE_INFINITY;

            for (int k = 0; k < candidates.size(); k++) {
                int candidate = candidates.get(k);
                double baseDistance = distances[from][candidate];
                if (useSurface && baseDistance >= bestScore) {
                    prunedCount += (candidates.size() - k);
                    break;
                }
                double factor = useSurface ? Math.max(1.0, surfaceFactors[from][candidate]) : 1.0;
                double weightedScore = baseDistance * factor;
                if (weightedScore < bestScore) {
                    bestScore = weightedScore;
                    bestCandidate = candidate;
                }
            }
            currentIndex = bestCandidate;
        }
        tour[n] = startIndex;
        return new NnResult(tour, 0.0, prunedCount, totalCandidatesCount);
    }

    private AlgoResult benchmarkNnPlusTwoOpt(double[][] distMatrix, double[][] surfMatrix, double[][] costMatrix, int startNode) {
        int n = costMatrix.length;
        int runs = (n <= 40) ? 50 : 15;
        for (int w = 0; w < 10; w++) {
            NnResult nn = solveNearestNeighborWithPruning(distMatrix, surfMatrix, startNode, true);
            solveTwoOptFast(costMatrix, nn.tour);
        }
        long startNs = System.nanoTime();
        int[] tour = null;
        for (int r = 0; r < runs; r++) {
            NnResult nn = solveNearestNeighborWithPruning(distMatrix, surfMatrix, startNode, true);
            tour = solveTwoOptFast(costMatrix, nn.tour);
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new AlgoResult(tour, avgMs);
    }

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

    private int[] relocateSegment(int[] tour, int startIdx, int segLen, int insertAfterIdx) {
        int len = tour.length;
        List<Integer> list = new ArrayList<>(len);
        List<Integer> seg = new ArrayList<>(segLen);
        for (int idx = 0; idx < len; idx++) {
            if (idx >= startIdx && idx < startIdx + segLen) {
                seg.add(tour[idx]);
            } else {
                list.add(tour[idx]);
            }
        }
        int targetPos = (insertAfterIdx < startIdx) ? (insertAfterIdx + 1) : (insertAfterIdx - segLen + 1);
        list.addAll(targetPos, seg);
        int[] res = new int[len];
        for (int idx = 0; idx < len; idx++) res[idx] = list.get(idx);
        return res;
    }

    private AlgoResult benchmarkThreeOptLkh(double[][] costMatrix, int[] initialTour) {
        int n = costMatrix.length;
        int runs = (n <= 20) ? 15 : (n <= 40 ? 4 : 2);
        solveThreeOptLkh(costMatrix, initialTour);
        long startNs = System.nanoTime();
        int[] tour = null;
        for (int r = 0; r < runs; r++) {
            tour = solveThreeOptLkh(costMatrix, initialTour);
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new AlgoResult(tour, avgMs);
    }

    private int[] solveThreeOptLkh(double[][] costMatrix, int[] initialTour) {
        int[] tour = solveTwoOptFast(costMatrix, initialTour);
        int n = tour.length - 1;
        double bestCost = tourCost(tour, costMatrix);
        boolean improved = true;
        int maxSpan = (n <= 35) ? n : 22; // Janela de candidatos LKH para viabilizar N=70..110 sem travar em O(n^4)

        while (improved) {
            improved = false;
            outer:
            for (int i = 1; i < n - 2; i++) {
                int jLimit = Math.min(n - 1, i + maxSpan);
                for (int j = i + 1; j < jLimit; j++) {
                    int kLimit = Math.min(n, j + maxSpan);
                    for (int k = j + 1; k < kLimit; k++) {
                        int[][] candidates = generateThreeOptMoves(tour, i, j, k);
                        for (int[] cand : candidates) {
                            double cCost = tourCost(cand, costMatrix);
                            if (cCost + 1e-9 < bestCost) {
                                tour = cand;
                                bestCost = cCost;
                                improved = true;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        return tour;
    }

    private int[][] generateThreeOptMoves(int[] tour, int i, int j, int k) {
        int len = tour.length;
        int[][] moves = new int[4][len];
        int idx = 0;
        for (int p = 0; p < i; p++) moves[0][idx++] = tour[p];
        for (int p = j; p < k; p++) moves[0][idx++] = tour[p];
        for (int p = i; p < j; p++) moves[0][idx++] = tour[p];
        for (int p = k; p < len; p++) moves[0][idx++] = tour[p];

        idx = 0;
        for (int p = 0; p < i; p++) moves[1][idx++] = tour[p];
        for (int p = j - 1; p >= i; p--) moves[1][idx++] = tour[p];
        for (int p = k - 1; p >= j; p--) moves[1][idx++] = tour[p];
        for (int p = k; p < len; p++) moves[1][idx++] = tour[p];

        idx = 0;
        for (int p = 0; p < i; p++) moves[2][idx++] = tour[p];
        for (int p = k - 1; p >= j; p--) moves[2][idx++] = tour[p];
        for (int p = i; p < j; p++) moves[2][idx++] = tour[p];
        for (int p = k; p < len; p++) moves[2][idx++] = tour[p];

        idx = 0;
        for (int p = 0; p < i; p++) moves[3][idx++] = tour[p];
        for (int p = j; p < k; p++) moves[3][idx++] = tour[p];
        for (int p = j - 1; p >= i; p--) moves[3][idx++] = tour[p];
        for (int p = k; p < len; p++) moves[3][idx++] = tour[p];

        return moves;
    }

    private AlgoResult benchmarkSimulatedAnnealing(double[][] costMatrix, int startNode) {
        int n = costMatrix.length;
        int runs = (n <= 20) ? 12 : (n <= 40 ? 6 : 4);
        solveSimulatedAnnealing(costMatrix, startNode, 42L);
        long startNs = System.nanoTime();
        int[] bestOverallTour = null;
        double bestOverallCost = Double.POSITIVE_INFINITY;

        for (int r = 0; r < runs; r++) {
            int[] tour = solveSimulatedAnnealing(costMatrix, startNode, 2000L + r);
            double cost = tourCost(tour, costMatrix);
            if (cost < bestOverallCost) {
                bestOverallCost = cost;
                bestOverallTour = tour;
            }
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new AlgoResult(bestOverallTour, avgMs);
    }

    private int[] solveSimulatedAnnealing(double[][] costMatrix, int startNode, long seed) {
        int n = costMatrix.length;
        Random rng = new Random(seed);

        NnResult nn = solveNearestNeighborWithPruning(costMatrix, new double[n][n], startNode, false);
        int[] currentTour = solveTwoOptFast(costMatrix, nn.tour);
        double currentCost = tourCost(currentTour, costMatrix);

        int[] bestTour = currentTour.clone();
        double bestCost = currentCost;

        double temperature = 80.0;
        final double coolingRate = 0.96;
        final double minTemperature = 0.02;
        final int markovChainLength = 35;

        while (temperature > minTemperature) {
            for (int m = 0; m < markovChainLength; m++) {
                int i = 1 + rng.nextInt(n - 2);
                int j = i + 1 + rng.nextInt(n - 1 - i);

                int[] candidate = currentTour.clone();
                if (rng.nextBoolean()) {
                    reverseSubSegment(candidate, i, j);
                } else {
                    int movedNode = candidate[i];
                    System.arraycopy(candidate, i + 1, candidate, i, j - i);
                    candidate[j] = movedNode;
                }
                double candCost = tourCost(candidate, costMatrix);
                double deltaE = candCost - currentCost;

                if (deltaE < 0 || rng.nextDouble() < Math.exp(-deltaE / temperature)) {
                    currentTour = candidate;
                    currentCost = candCost;
                    if (currentCost < bestCost) {
                        bestCost = currentCost;
                        bestTour = currentTour.clone();
                    }
                }
            }
            temperature *= coolingRate;
        }
        return solveTwoOptFast(costMatrix, bestTour);
    }

    private AlgoResult benchmarkGeneticAlgorithm(double[][] costMatrix, int startNode, boolean memeticLocalSearch) {
        int n = costMatrix.length;
        int runs = (n <= 20) ? 10 : (n <= 40 ? 4 : 2);
        solveGeneticAlgorithm(costMatrix, startNode, 42L, memeticLocalSearch);
        long startNs = System.nanoTime();
        int[] bestOverallTour = null;
        double bestOverallCost = Double.POSITIVE_INFINITY;

        for (int r = 0; r < runs; r++) {
            int[] tour = solveGeneticAlgorithm(costMatrix, startNode, 1000L + r, memeticLocalSearch);
            double cost = tourCost(tour, costMatrix);
            if (cost < bestOverallCost) {
                bestOverallCost = cost;
                bestOverallTour = tour;
            }
        }
        double avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / runs;
        return new AlgoResult(bestOverallTour, avgMs);
    }

    private int[] solveGeneticAlgorithm(double[][] costMatrix, int startNode, long seed, boolean memetic) {
        int n = costMatrix.length;
        int popSize = (n <= 40) ? 22 : 16;
        int generations = (n <= 40) ? 30 : 20;
        double mutationRate = 0.20;
        Random rng = new Random(seed);

        List<int[]> population = new ArrayList<>(popSize);
        for (int p = 0; p < popSize; p++) {
            int[] ind = randomTour(n, startNode, rng);
            if (memetic && p < 4) {
                ind = solveTwoOptFast(costMatrix, ind);
            }
            population.add(ind);
        }

        for (int g = 0; g < generations; g++) {
            population.sort(Comparator.comparingDouble(ind -> tourCost(ind, costMatrix)));
            List<int[]> nextGen = new ArrayList<>(popSize);
            nextGen.add(population.get(0).clone());
            nextGen.add(population.get(1).clone());

            while (nextGen.size() < popSize) {
                int[] p1 = tournamentSelect(population, costMatrix, rng);
                int[] p2 = tournamentSelect(population, costMatrix, rng);
                int[] child = orderCrossover(p1, p2, n, rng);

                if (rng.nextDouble() < mutationRate) {
                    int idxA = 1 + rng.nextInt(n - 1);
                    int idxB = 1 + rng.nextInt(n - 1);
                    int tmp = child[idxA];
                    child[idxA] = child[idxB];
                    child[idxB] = tmp;
                }
                if (memetic && rng.nextDouble() < 0.30) {
                    child = solveTwoOptFast(costMatrix, child);
                }
                nextGen.add(child);
            }
            population = nextGen;
        }
        population.sort(Comparator.comparingDouble(ind -> tourCost(ind, costMatrix)));
        return memetic ? solveTwoOptFast(costMatrix, population.get(0)) : population.get(0);
    }

    private void applyAlnsRepair(
            List<Integer> route,
            List<Integer> removedPool,
            int rOp,
            double[][] costMatrix) {
        if (rOp == 0) {
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

                    double regret = (secondBest == Double.POSITIVE_INFINITY) ? firstBest : (secondBest - firstBest);
                    if (regret > maxRegret) {
                        maxRegret = regret;
                        chosenPoolIdx = p;
                        chosenInsertPos = bestPosForNode;
                    }
                }
                int node = removedPool.remove(chosenPoolIdx);
                route.add(chosenInsertPos, node);
            }
        }
    }

    private int rouletteSelect(double[] weights, Random rng) {
        double sum = 0.0;
        for (double w : weights) sum += w;
        double r = rng.nextDouble() * sum;
        double acc = 0.0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (acc >= r) return i;
        }
        return weights.length - 1;
    }

    // =========================================================================
    // DADOS REAIS DOS DOIS POOLS (CAMPINAS/RMC 110 PONTOS + SANTOS/GUARUJÁ 15 PONTOS)
    // =========================================================================
    private List<StopPoint> getCampinasMasterPool35() {
        return getCampinasMasterPool110().subList(0, 35);
    }

    private List<StopPoint> loadStopsFromJsonFile(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> rawList = null;

            File f = new File(path);
            if (f.exists()) {
                rawList = mapper.readValue(f, new TypeReference<List<Map<String, Object>>>() {});
            } else {
                InputStream is1 = getClass().getClassLoader().getResourceAsStream(path);
                if (is1 == null && !path.startsWith("scenarios/")) {
                    is1 = getClass().getClassLoader().getResourceAsStream("scenarios/" + path);
                }
                if (is1 != null) {
                    try (InputStream stream = is1) {
                        rawList = mapper.readValue(stream, new TypeReference<List<Map<String, Object>>>() {});
                    }
                }
            }

            if (rawList != null && !rawList.isEmpty()) {
                List<StopPoint> points = new ArrayList<>();
                for (int i = 0; i < rawList.size(); i++) {
                    Map<String, Object> m = rawList.get(i);
                    int id = m.containsKey("id") ? ((Number) m.get("id")).intValue() : i;
                    String address = m.getOrDefault("address", m.getOrDefault("name", "Ponto " + id)).toString();
                    double lat = ((Number) m.getOrDefault("latitude", 0.0)).doubleValue();
                    double lng = ((Number) m.getOrDefault("longitude", 0.0)).doubleValue();
                    String formatted = m.getOrDefault("formattedAddress", address).toString();
                    double surf = m.containsKey("characteristicSurfaceFactor") ? ((Number) m.get("characteristicSurfaceFactor")).doubleValue() : 1.0;
                    boolean cached = m.containsKey("cachedFromGoogle") ? (Boolean) m.get("cachedFromGoogle") : (lat != 0.0 && lng != 0.0);
                    points.add(new StopPoint(id, address, lat, lng, formatted, surf, cached));
                }
                System.out.printf("  [JSON CARREGADO] %,d pontos carregados com sucesso: %s%n", points.size(), path);
                return points;
            }
        } catch (Exception e) {
            System.err.println("  [AVISO] Falha ao ler arquivo JSON '" + path + "': " + e.getMessage());
        }
        return null;
    }

    public List<StopPoint> loadScenarioStops(String scenarioCode) {
        String clean = scenarioCode.trim().toLowerCase();
        String[] candidates = {
            clean + ".json",
            "scenarios/" + clean + ".json",
            "src/main/resources/scenarios/" + clean + ".json",
            "src/test/resources/scenarios/" + clean + ".json",
            "cenarios/" + clean + ".json"
        };
        for (String c : candidates) {
            List<StopPoint> points = loadStopsFromJsonFile(c);
            if (points != null && !points.isEmpty()) return points;
        }
        return null;
    }

    private List<StopPoint> getCampinasMasterPool110() {
        // 1. Tentar caminho configurado via parâmetro/CLI
        if (configuredExternalJsonPath != null && !configuredExternalJsonPath.isBlank()) {
            List<StopPoint> loaded = loadStopsFromJsonFile(configuredExternalJsonPath);
            if (loaded != null && !loaded.isEmpty()) return loaded;
        }
        // 2. Tentar propriedade de sistema ou variável de ambiente
        String envPath = System.getProperty("benchmark.addresses.file", System.getenv("BENCHMARK_ADDRESSES_FILE"));
        if (envPath != null && !envPath.isBlank()) {
            List<StopPoint> loaded = loadStopsFromJsonFile(envPath);
            if (loaded != null && !loaded.isEmpty()) return loaded;
        }
        // 3. Tentar carregar de arquivo JSON local relativo
        String[] localCandidates = {
            "src/main/resources/benchmark_addresses.json",
            "src/test/resources/benchmark_addresses.json",
            "benchmark_addresses.json"
        };
        for (String candidate : localCandidates) {
            List<StopPoint> loaded = loadStopsFromJsonFile(candidate);
            if (loaded != null && !loaded.isEmpty()) return loaded;
        }

        return List.of(
                // --- Polos Urbanos Centrais & Cambuí (IDs 0..11 -> C1 Carga Pesada B2B N=12) ---
                new StopPoint(0,  "Av. Francisco Glicério",       -22.904287, -47.060046, "Av. Francisco Glicério - Centro, Campinas - SP", 1.00, true),
                new StopPoint(1,  "Rua Barão de Jaguara",         -22.907477, -47.057499, "Rua Barão de Jaguara - Centro, Campinas - SP", 1.00, true),
                new StopPoint(2,  "Av. Júlio de Mesquita",        -22.900235, -47.054601, "Av. Júlio de Mesquita - Cambuí, Campinas - SP", 1.02, true),
                new StopPoint(3,  "Rua General Osório",           -22.903764, -47.059365, "R. Gen. Osório - Centro, Campinas - SP", 1.00, true),
                new StopPoint(4,  "Av. José de Sousa Campos",     -22.890797, -47.046349, "Av. José de Sousa Campos (Norte-Sul) - Campinas - SP", 1.00, true),
                new StopPoint(5,  "Rua Conceição",                -22.903195, -47.054007, "R. Conceição - Centro, Campinas - SP", 1.00, true),
                new StopPoint(6,  "Av. Albino J. B. de Oliveira", -22.822660, -47.080435, "Av. Albino J. B. de Oliveira - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(7,  "Rua Dr. Quirino",              -22.906078, -47.057320, "R. Dr. Quirino - Centro, Campinas - SP", 1.00, true),
                new StopPoint(8,  "Av. Aquidabã",                 -22.910223, -47.055315, "Av. Aquidabã - Campinas - SP", 1.00, true),
                new StopPoint(9,  "Rua Cel. Quirino",             -22.897564, -47.052931, "R. Cel. Quirino - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(10, "Av. Iguatemi",                 -22.893593, -47.026856, "Av. Iguatemi - Vila Brandina, Campinas - SP", 1.00, true),
                new StopPoint(11, "Av. Brasil",                   -22.888753, -47.062819, "Av. Brasil - Guanabara, Campinas - SP", 1.00, true),

                // --- Expansão Urbana Assimétrica (IDs 12..17) ---
                new StopPoint(12, "Av. Andrade Neves",            -22.891378, -47.077185, "Av. Andrade Neves - Botafogo, Campinas - SP", 1.00, true),
                new StopPoint(13, "Av. Amoreiras",                -22.931050, -47.066310, "Av. das Amoreiras - São Bernardo, Campinas - SP", 1.00, true),
                new StopPoint(14, "Av. John Boyd Dunlop",         -22.932467, -47.114957, "Av. John Boyd Dunlop - Jardim Ipaussurama, Campinas - SP", 1.02, true),
                new StopPoint(15, "Av. Heitor Penteado",          -22.871299, -47.045017, "Av. Dr. Heitor Penteado (Taquaral) - Campinas - SP", 1.00, true),
                new StopPoint(16, "Av. Guilherme Campos",         -22.851181, -47.062857, "Parque D. Pedro Shopping - Santa Genebra, Campinas - SP", 1.00, true),
                new StopPoint(17, "Av. Prestes Maia",             -22.919360, -47.053751, "Av. Prestes Maia - Jardim do Trevo, Campinas - SP", 1.00, true),

                // --- Eixo Rural / Vias Não Pavimentadas / Paralelepípedo (IDs 18..25 -> C3 Rural N=18) ---
                new StopPoint(18, "Rua Cel. Alfredo Augusto do Nascimento, Sousas", -22.879452, -46.962014, "Centro Histórico de Sousas (Paralelepípedo), Campinas - SP", 1.12, true),
                new StopPoint(19, "Rua Heitor Penteado, Joaquim Egídio",            -22.887816, -46.942296, "Vila de Joaquim Egídio (Pedra/Paralelepípedo), Campinas - SP", 1.15, true),
                new StopPoint(20, "Estrada Municipal Luciano Rocha, Joaquim Egídio",-22.896200, -46.921500, "Estrada Rural Joaquim Egídio (Terra/Cascalho), Campinas - SP", 1.24, true),
                new StopPoint(21, "Rod. José Bonifácio Coutinho Nogueira",          -22.890757, -46.938204, "Acesso Serra das Cabras / Sousas, Campinas - SP", 1.18, true),
                new StopPoint(22, "Estrada do Capricórnio, Joaquim Egídio",         -22.891881, -46.835151, "Estrada Monte Urânia / Capricórnio (Não Pavimentada), Campinas - SP", 1.28, true),
                new StopPoint(23, "Estrada Municipal da Rhodia, Barão Geraldo",     -22.756956, -47.059542, "Corredor Rural Rio das Pedras / Barão Geraldo, Campinas - SP", 1.16, true),
                new StopPoint(24, "Estrada Municipal das Cabras, Joaquim Egídio",   -22.868500, -46.912400, "Estrada das Cabras (Terra/Cascalho), Campinas - SP", 1.26, true),
                new StopPoint(25, "Rua Maneco Rosa, Joaquim Egídio",                -22.889100, -46.939500, "Trilha Rural Joaquim Egídio, Campinas - SP", 1.22, true),

                // --- Corredor Rodoviário / Região Metropolitana RMC (IDs 26..34 -> C4 Comercial B2B N=35) ---
                new StopPoint(26, "Aeroporto Internacional de Viracopos",           -23.004982, -47.142572, "Terminal de Cargas Viracopos (Rod. Santos Dumont), Campinas - SP", 1.00, true),
                new StopPoint(27, "Av. dos Esportes, Centro, Valinhos - SP",        -22.973098, -46.997168, "Polo Logístico Valinhos (Via Anhanguera), Valinhos - SP", 1.00, true),
                new StopPoint(28, "Av. Benedito Storani, Centro, Vinhedo - SP",     -23.026775, -46.978175, "Distrito Industrial Vinhedo, Vinhedo - SP", 1.00, true),
                new StopPoint(29, "Av. José Paulino, Centro, Paulínia - SP",        -22.773567, -47.146081, "Polo Petroquímico Paulínia (Rod. Zeferino Vaz), Paulínia - SP", 1.00, true),
                new StopPoint(30, "Av. Rebouças, Centro, Sumaré - SP",              -22.823529, -47.271009, "Centro Logístico Sumaré (Via Anhanguera), Sumaré - SP", 1.00, true),
                new StopPoint(31, "Av. da Emancipação, Hortolândia - SP",           -22.883080, -47.195453, "Corredor Industrial Hortolândia (SP-101), Hortolândia - SP", 1.00, true),
                new StopPoint(32, "Av. Presidente Kennedy, Indaiatuba - SP",        -23.081600, -47.210400, "Polo Comercial Indaiatuba, Indaiatuba - SP", 1.00, true),
                new StopPoint(33, "Av. Brasil, Centro, Monte Mor - SP",             -22.945200, -47.312500, "Distrito Logístico Monte Mor, Monte Mor - SP", 1.04, true),
                new StopPoint(34, "Av. Dr. Moraes Salles, Centro, Campinas - SP",   -22.905800, -47.052100, "Eixo Moraes Salles, Campinas - SP", 1.00, true),

                // --- E-commerce Last-Mile Padrão Vans (IDs 35..69 -> C5 N=70) ---
                new StopPoint(35, "Rua Barão de Atibaia, Guanabara",                -22.891500, -47.066200, "R. Barão de Atibaia - Guanabara, Campinas - SP", 1.00, true),
                new StopPoint(36, "Av. Orosimbo Maia, Cambuí",                      -22.897200, -47.051500, "Av. Orosimbo Maia - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(37, "Rua Maria Monteiro, Cambuí",                     -22.896100, -47.049800, "R. Maria Monteiro - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(38, "Av. Dr. Alberto Sarmento, Bonfim",               -22.897800, -47.076500, "Av. Dr. Alberto Sarmento - Bonfim, Campinas - SP", 1.00, true),
                new StopPoint(39, "Rua Buarque de Macedo, Vila Nova",               -22.883500, -47.068200, "R. Buarque de Macedo - Vila Nova, Campinas - SP", 1.00, true),
                new StopPoint(40, "Av. Imperatriz Leopoldina, Vila Nova",           -22.879000, -47.070500, "Av. Imperatriz Leopoldina - Vila Nova, Campinas - SP", 1.00, true),
                new StopPoint(41, "Av. Governador Pedro de Toledo, Bonfim",         -22.902500, -47.078200, "Av. Gov. Pedro de Toledo - Bonfim, Campinas - SP", 1.00, true),
                new StopPoint(42, "Rua Saldanha Marinho, Centro",                   -22.902100, -47.063200, "R. Saldanha Marinho - Centro, Campinas - SP", 1.00, true),
                new StopPoint(43, "Rua Benjamin Constant, Centro",                  -22.901500, -47.057800, "R. Benjamin Constant - Centro, Campinas - SP", 1.00, true),
                new StopPoint(44, "Rua Ferreira Penteado, Centro",                  -22.907000, -47.055200, "R. Ferreira Penteado - Centro, Campinas - SP", 1.00, true),
                new StopPoint(45, "Av. Senador Saraiva, Centro",                    -22.905500, -47.062000, "Av. Senador Saraiva - Centro, Campinas - SP", 1.00, true),
                new StopPoint(46, "Rua Carolina Florence, Vila Nova",               -22.881200, -47.065000, "R. Carolina Florence - Vila Nova, Campinas - SP", 1.00, true),
                new StopPoint(47, "Av. Barão de Itapura, Botafogo",                 -22.893000, -47.058500, "Av. Barão de Itapura - Botafogo, Campinas - SP", 1.00, true),
                new StopPoint(48, "Rua Sacramento, Centro",                         -22.902800, -47.056000, "R. Sacramento - Centro, Campinas - SP", 1.00, true),
                new StopPoint(49, "Rua Luzitana, Centro",                           -22.904800, -47.058000, "R. Luzitana - Centro, Campinas - SP", 1.00, true),
                new StopPoint(50, "Av. Princesa d'Oeste, Proença",                  -22.907500, -47.044000, "Av. Princesa d'Oeste - Jardim Proença, Campinas - SP", 1.00, true),
                new StopPoint(51, "Av. Engenheiro Carlos Stevenson, Nova Campinas", -22.903000, -47.039500, "Av. Eng. Carlos Stevenson - Nova Campinas, Campinas - SP", 1.00, true),
                new StopPoint(52, "Rua Coronel Silva Telles, Cambuí",               -22.895000, -47.052000, "R. Cel. Silva Telles - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(53, "Rua dos Alecrins, Cambuí",                       -22.898500, -47.048000, "R. dos Alecrins - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(54, "Rua Doutor Shigeo Mori, Barão Geraldo",          -22.818500, -47.066500, "R. Dr. Shigeo Mori - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(55, "Av. Romeu Tórtima, Barão Geraldo",               -22.827000, -47.074000, "Av. Romeu Tórtima - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(56, "Rua Roxo Moreira, Cidade Universitária",         -22.824500, -47.069000, "R. Roxo Moreira - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(57, "Av. Santa Isabel, Barão Geraldo",                -22.821000, -47.087000, "Av. Santa Isabel - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(58, "Av. Modesto Fernandes, Barão Geraldo",           -22.816000, -47.091000, "Av. Modesto Fernandes - Barão Geraldo, Campinas - SP", 1.00, true),
                new StopPoint(59, "Av. Baden Powell, Jardim Nova Europa",           -22.932000, -47.051000, "Av. Baden Powell - Jardim Nova Europa, Campinas - SP", 1.00, true),
                new StopPoint(60, "Av. Estados Unidos, Jardim Nova Europa",         -22.935000, -47.046000, "Av. Estados Unidos - Jardim Nova Europa, Campinas - SP", 1.00, true),
                new StopPoint(61, "Av. Washington Luiz, Vila Marieta",              -22.923000, -47.045000, "Av. Washington Luiz - Vila Marieta, Campinas - SP", 1.00, true),
                new StopPoint(62, "Av. Marechal Carmona, Ponte Preta",              -22.915000, -47.049000, "Av. Mal. Carmona - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(63, "Rua Abolição, Ponte Preta",                      -22.918000, -47.042000, "R. Abolição - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(64, "Av. Saudade, Ponte Preta",                       -22.916500, -47.053500, "Av. da Saudade - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(65, "Av. Dr. Ângelo Simões, Ponte Preta",             -22.912500, -47.046500, "Av. Dr. Ângelo Simões - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(66, "Av. Eng. Antônio Francisco de Paula Souza",      -22.929000, -47.035000, "Av. Eng. Antônio Francisco de Paula Souza - Campinas - SP", 1.00, true),
                new StopPoint(67, "Rua Piracicaba, Jardim Novo Campos Elíseos",     -22.924000, -47.098000, "R. Piracicaba - Jardim Novo Campos Elíseos, Campinas - SP", 1.00, true),
                new StopPoint(68, "Av. Ruy Rodriguez, Parque Industrial",           -22.941000, -47.105000, "Av. Ruy Rodriguez - Parque Industrial, Campinas - SP", 1.00, true),
                new StopPoint(69, "Av. Coacyara, Parque Industrial",                -22.937000, -47.092000, "Av. Coacyara - Parque Industrial, Campinas - SP", 1.00, true),

                // --- E-commerce Alta Densidade / Black Friday (IDs 70..109 -> C6 N=110) ---
                new StopPoint(70, "Av. das Amoreiras, Jardim Leonor",               -22.928000, -47.078000, "Av. das Amoreiras - Jardim Leonor, Campinas - SP", 1.00, true),
                new StopPoint(71, "Rua Sylvio Moro, Vila Industrial",               -22.911000, -47.069000, "R. Sylvio Moro - Vila Industrial, Campinas - SP", 1.00, true),
                new StopPoint(72, "Rua Pereira Tangerino, Bonfim",                  -22.894000, -47.081000, "R. Pereira Tangerino - Bonfim, Campinas - SP", 1.00, true),
                new StopPoint(73, "Av. Gov. Capanema, Jardim Chapadão",             -22.885000, -47.082000, "Av. Gov. Capanema - Jardim Chapadão, Campinas - SP", 1.00, true),
                new StopPoint(74, "Rua Dr. Arnaldo de Carvalho, Jardim Chapadão",   -22.889000, -47.085000, "R. Dr. Arnaldo de Carvalho - Jardim Chapadão, Campinas - SP", 1.00, true),
                new StopPoint(75, "Av. Marechal Rondon, Jardim Chapadão",           -22.892000, -47.091000, "Av. Mal. Rondon - Jardim Chapadão, Campinas - SP", 1.00, true),
                new StopPoint(76, "Rua Alberto Faria, Castelo",                     -22.887000, -47.078000, "R. Alberto Faria - Castelo, Campinas - SP", 1.00, true),
                new StopPoint(77, "Av. Andrade Neves, Castelo",                     -22.886000, -47.082500, "Av. Andrade Neves - Castelo, Campinas - SP", 1.00, true),
                new StopPoint(78, "Rua Santo Antonio Claret, Castelo",              -22.890000, -47.074000, "R. Santo Antonio Claret - Castelo, Campinas - SP", 1.00, true),
                new StopPoint(79, "Av. Francisco José de Camargo Andrade, Castelo", -22.888500, -47.076000, "Av. Francisco José de Camargo Andrade - Castelo, Campinas - SP", 1.00, true),
                new StopPoint(80, "Av. Barão de Monte Santo, São Bernardo",         -22.925000, -47.071000, "Av. Barão de Monte Santo - São Bernardo, Campinas - SP", 1.00, true),
                new StopPoint(81, "Rua Alves do Banho, São Bernardo",               -22.922000, -47.068000, "R. Alves do Banho - São Bernardo, Campinas - SP", 1.00, true),
                new StopPoint(82, "Av. João Jorge, Vila Industrial",                -22.914000, -47.061000, "Av. João Jorge - Vila Industrial, Campinas - SP", 1.00, true),
                new StopPoint(83, "Rua Sete de Setembro, Vila Industrial",          -22.912000, -47.065000, "R. Sete de Setembro - Vila Industrial, Campinas - SP", 1.00, true),
                new StopPoint(84, "Av. General Carneiro, Ponte Preta",              -22.917000, -47.047000, "Av. Gen. Carneiro - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(85, "Rua Engenheiro Monlevade, Ponte Preta",          -22.919000, -47.044000, "R. Eng. Monlevade - Ponte Preta, Campinas - SP", 1.00, true),
                new StopPoint(86, "Av. Rotary, Jardim das Paineiras",               -22.899000, -47.021000, "Av. Rotary - Jardim das Paineiras, Campinas - SP", 1.00, true),
                new StopPoint(87, "Rua José Bonifácio, Jardim Flamboyant",          -22.892000, -47.034000, "R. José Bonifácio - Jardim Flamboyant, Campinas - SP", 1.00, true),
                new StopPoint(88, "Av. José Bonifácio, Jardim Flamboyant",          -22.894000, -47.032000, "Av. José Bonifácio - Jardim Flamboyant, Campinas - SP", 1.00, true),
                new StopPoint(89, "Rua Maria Conceição F. de Andrade, Nova Campinas", -22.901000, -47.037000, "R. Maria Conceição F. de Andrade - Nova Campinas, Campinas - SP", 1.00, true),
                new StopPoint(90, "Av. Carlos Grimaldi, Jardim Conceição",          -22.879000, -47.036000, "Av. Carlos Grimaldi - Jardim Conceição, Campinas - SP", 1.00, true),
                new StopPoint(91, "Rua Bento de Arruda Camargo, Parque São Quirino", -22.868000, -47.040000, "R. Bento de Arruda Camargo - Parque São Quirino, Campinas - SP", 1.00, true),
                new StopPoint(92, "Av. Diogo Álvares, Parque São Quirino",          -22.865000, -47.045000, "Av. Diogo Álvares - Parque São Quirino, Campinas - SP", 1.00, true),
                new StopPoint(93, "Av. Luiz Smânio, Jardim Chapadão",               -22.878000, -47.086000, "Av. Luiz Smânio - Jardim Chapadão, Campinas - SP", 1.00, true),
                new StopPoint(94, "Av. Theodureto de Almeida Camargo, Vila Nova",   -22.874000, -47.068000, "Av. Theodureto de Almeida Camargo - Vila Nova, Campinas - SP", 1.00, true),
                new StopPoint(95, "Rua Doutor Buarque de Macedo, Taquaral",         -22.876000, -47.057000, "R. Dr. Buarque de Macedo - Taquaral, Campinas - SP", 1.00, true),
                new StopPoint(96, "Av. Nossa Senhora de Fátima, Taquaral",          -22.872000, -47.051000, "Av. N. Sra. de Fátima - Taquaral, Campinas - SP", 1.00, true),
                new StopPoint(97, "Rua Paula Bueno, Taquaral",                      -22.879000, -47.050000, "R. Paula Bueno - Taquaral, Campinas - SP", 1.00, true),
                new StopPoint(98, "Rua Castro Alves, Taquaral",                     -22.883000, -47.052000, "R. Castro Alves - Taquaral, Campinas - SP", 1.00, true),
                new StopPoint(99, "Rua Padre Almeida, Cambuí",                      -22.894000, -47.055000, "R. Padre Almeida - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(100, "Rua Sampainho, Cambuí",                         -22.897000, -47.056000, "R. Sampainho - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(101, "Rua Coronel Quirino, Cambuí",                   -22.899000, -47.050000, "R. Cel. Quirino - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(102, "Rua Santa Cruz, Cambuí",                        -22.898000, -47.053000, "R. Santa Cruz - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(103, "Rua Major Solon, Cambuí",                       -22.900000, -47.057000, "R. Major Solon - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(104, "Rua Santos Dumont, Cambuí",                     -22.901000, -47.054000, "R. Santos Dumont - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(105, "Rua Olavo Bilac, Cambuí",                       -22.896500, -47.051000, "R. Olavo Bilac - Cambuí, Campinas - SP", 1.00, true),
                new StopPoint(106, "Av. Barão de Itapura, Guanabara",               -22.887000, -47.065000, "Av. Barão de Itapura - Guanabara, Campinas - SP", 1.00, true),
                new StopPoint(107, "Rua Tiradentes, Guanabara",                     -22.889000, -47.067000, "R. Tiradentes - Guanabara, Campinas - SP", 1.00, true),
                new StopPoint(108, "Av. Alberto Sarmento, Castelo",                 -22.891000, -47.080000, "Av. Alberto Sarmento - Castelo, Campinas - SP", 1.00, true),
                new StopPoint(109, "Av. Francisco Glicério, Centro",                -22.906500, -47.058500, "Av. Francisco Glicério - Centro, Campinas - SP", 1.00, true)
        );
    }

    private List<StopPoint> getSantosGuarujaPool15() {
        return List.of(
                new StopPoint(0,  "Av. Conselheiro Nébias, 800, Boqueirão, Santos - SP",          -23.969074, -46.324185, "Base Santos - Boqueirão, Santos - SP", 1.00, true),
                new StopPoint(1,  "Av. Almirante Saldanha da Gama, 100, Ponta da Praia, Santos",  -23.990043, -46.299833, "Ponta da Praia (Margem Oeste do Canal), Santos - SP", 1.00, true),
                new StopPoint(2,  "Av. Thiago Ferreira, 500, Vicente de Carvalho, Guarujá",       -23.941662, -46.304048, "Vicente de Carvalho (Margem Leste do Canal), Guarujá - SP", 1.04, true),
                new StopPoint(3,  "Praça das Bandeiras, Gonzaga, Santos - SP",                    -23.969846, -46.333166, "Gonzaga, Santos - SP", 1.00, true),
                new StopPoint(4,  "Av. Puglisi, 400, Pitangueiras, Guarujá - SP",                 -23.993413, -46.257726, "Centro / Pitangueiras, Guarujá - SP", 1.00, true),
                new StopPoint(5,  "Av. Ana Costa, 200, Vila Mathias, Santos - SP",                -23.952760, -46.331360, "Vila Mathias, Santos - SP", 1.00, true),
                new StopPoint(6,  "Av. Miguel Estéfano, 2500, Enseada, Guarujá - SP",             -23.986225, -46.226386, "Praia da Enseada, Guarujá - SP", 1.02, true),
                new StopPoint(7,  "Via Anchieta, Alemoa, Santos - SP",                            -23.926679, -46.378807, "Terminal Retroportuário Alemoa, Santos - SP", 1.05, true),
                new StopPoint(8,  "Av. General Monteiro de Barros, Astúrias, Guarujá - SP",       -24.010223, -46.266289, "Ponta das Astúrias, Guarujá - SP", 1.00, true),
                new StopPoint(9,  "Praça Barão do Rio Branco, Centro, São Vicente - SP",          -23.966709, -46.385621, "Centro de São Vicente, São Vicente - SP", 1.00, true),
                new StopPoint(10, "Av. Nove de Abril, 2000, Centro, Cubatão - SP",                -23.883329, -46.421370, "Entroncamento Rodoviário Piaçaguera, Cubatão - SP", 1.02, true),
                new StopPoint(11, "Av. Santos Dumont, 1500, Sítio Paecará, Guarujá - SP",         -23.954630, -46.291582, "Distrito Portuário Paecará, Guarujá - SP", 1.04, true),
                new StopPoint(12, "Av. Presidente Wilson, José Menino, Santos - SP",              -23.971200, -46.348900, "Orla José Menino, Santos - SP", 1.00, false),
                new StopPoint(13, "Av. Dom Pedro I, Enseada, Guarujá - SP",                       -23.978500, -46.241200, "Corredor Dom Pedro I, Guarujá - SP", 1.02, false),
                new StopPoint(14, "Av. Martins Fontes, Saboó, Santos - SP",                       -23.938400, -46.342100, "Acesso Portuário Saboó, Santos - SP", 1.03, false)
        );
    }

    private boolean checkQuotaSafety(
            List<StopPoint> poolA,
            List<StopPoint> poolB,
            boolean hasGoogleKey,
            boolean hasOrsKey) {
        long uncachedA = poolA.stream().filter(s -> !s.cachedFromGoogle).count();
        long uncachedB = poolB.stream().filter(s -> !s.cachedFromGoogle).count();
        int estimatedGoogleReqs = hasGoogleKey ? (int) (uncachedA + uncachedB) : 0;
        int estimatedOrsReqs = hasOrsKey ? 5 : 0;

        System.out.println("\n----------------------------------------------------------------------------------------------------------");
        System.out.println("  [GUARDIÃO DE RECURSOS DE API & COMPLEXIDADE - BATERIA DE MERCADO (N=12 até N=110)]");
        System.out.printf("  - Requisições Google Maps Geocoding estimadas : %d novos endereços (Limite seguro: %d)%n",
                estimatedGoogleReqs, MAX_SAFE_GOOGLE_GEOCODE_REQUESTS);
        System.out.printf("  - Requisições OpenRouteService (ORS) estimadas: <= %d (1 matriz 35x35 + 1 matriz 15x15 | Limite: %d)%n",
                estimatedOrsReqs, MAX_SAFE_ORS_REQUESTS_PER_RUN);
        System.out.println("----------------------------------------------------------------------------------------------------------");

        return estimatedGoogleReqs <= MAX_SAFE_GOOGLE_GEOCODE_REQUESTS
                && estimatedOrsReqs <= MAX_SAFE_ORS_REQUESTS_PER_RUN;
    }

    private List<StopPoint> resolveCoordinatesWithGoogleApi(List<StopPoint> stops, String googleApiKey) {
        if (googleApiKey == null || googleApiKey.isBlank()) {
            return stops;
        }
        List<StopPoint> resolved = new ArrayList<>(stops.size());
        for (StopPoint stop : stops) {
            if (stop.cachedFromGoogle) {
                resolved.add(stop);
                continue;
            }
            try {
                String encodedAddress = URLEncoder.encode(stop.address + ", Brasil", StandardCharsets.UTF_8);
                String url = GOOGLE_GEOCODE_URL + "?address=" + encodedAddress + "&key=" + googleApiKey;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                googleRequestsCount++;
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                if (body.contains("\"status\" : \"OK\"") || body.contains("\"status\":\"OK\"")) {
                    double[] latLng = parseGoogleGeocodeLatLng(body, stop.latitude, stop.longitude);
                    String formatted = parseGoogleFormattedAddress(body, stop.formattedAddress);
                    resolved.add(new StopPoint(stop.id, stop.address, latLng[0], latLng[1], formatted, stop.characteristicSurfaceFactor, true));
                } else {
                    resolved.add(stop);
                }
            } catch (Exception e) {
                resolved.add(stop);
            }
        }
        return resolved;
    }

    private MatricesData buildRoadMatrices(List<StopPoint> stops, String orsApiKey, boolean isCampinasPool) {
        int n = stops.size();
        double[][] distanceKm = new double[n][n];
        double[][] durationMin = new double[n][n];
        double[][] surfaceFactors = new double[n][n];
        double[][] weightedCost = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    surfaceFactors[i][j] = 1.0;
                } else {
                    surfaceFactors[i][j] = Math.max(1.0, (stops.get(i).characteristicSurfaceFactor + stops.get(j).characteristicSurfaceFactor) / 2.0);
                }
            }
        }

        if (orsApiKey != null && !orsApiKey.isBlank() && n <= 50) {
            try {
                StringBuilder coordsJson = new StringBuilder("[");
                for (int i = 0; i < n; i++) {
                    if (i > 0) coordsJson.append(",");
                    coordsJson.append(String.format(Locale.US, "[%.6f,%.6f]", stops.get(i).longitude, stops.get(i).latitude));
                }
                coordsJson.append("]");

                String payload = String.format(
                        "{\"locations\":%s,\"metrics\":[\"distance\",\"duration\"],\"units\":\"km\",\"resolve_locations\":false}",
                        coordsJson
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ORS_BASE_URL + "/v2/matrix/driving-car"))
                        .header("Authorization", orsApiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(20))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                long t0 = System.nanoTime();
                orsMatrixRequestsCount++;
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String body = response.body();
                    double[][] parsedDist = parse2dMatrixFromJson(body, "distances", n);
                    double[][] parsedDur = parse2dMatrixFromJson(body, "durations", n);
                    for (int i = 0; i < n; i++) {
                        for (int j = 0; j < n; j++) {
                            distanceKm[i][j] = parsedDist[i][j];
                            durationMin[i][j] = parsedDur[i][j] / 60.0;
                        }
                    }
                    double networkMs = (System.nanoTime() - t0) / 1_000_000.0;
                    System.out.printf(Locale.US, "  [ORS MATRIX OK] Matriz %dx%d (%s) obtida via /v2/matrix em %.2f ms (1 única requisição).%n",
                            n, n, isCampinasPool ? "Campinas/Rural/RMC" : "Santos/Guarujá/Cubatão", networkMs);
                } else {
                    fillFallbackCampinasMatrices(stops, distanceKm, durationMin, surfaceFactors);
                }
            } catch (Exception e) {
                fillFallbackCampinasMatrices(stops, distanceKm, durationMin, surfaceFactors);
            }
        } else {
            fillFallbackCampinasMatrices(stops, distanceKm, durationMin, surfaceFactors);
        }

        if (!isCampinasPool) {
            boolean[] isGuaruja = { false, false, true, false, true, false, true, false, true, false, false, true, false, true, false };
            int cubataoHub = 10;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (i != j && isGuaruja[i] != isGuaruja[j] && i != cubataoHub && j != cubataoHub) {
                        double roadViaCubataoDist = distanceKm[i][cubataoHub] + distanceKm[cubataoHub][j];
                        double roadViaCubataoDur = durationMin[i][cubataoHub] + durationMin[cubataoHub][j];
                        if (distanceKm[i][j] < 20.0 && roadViaCubataoDist > 25.0) {
                            distanceKm[i][j] = roadViaCubataoDist;
                            durationMin[i][j] = roadViaCubataoDur;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                weightedCost[i][j] = distanceKm[i][j] * surfaceFactors[i][j];
            }
        }
        return new MatricesData(distanceKm, durationMin, surfaceFactors, weightedCost);
    }

    private List<StopPoint> sliceStops(List<StopPoint> master, int[] indices) {
        List<StopPoint> sub = new ArrayList<>(indices.length);
        for (int idx = 0; idx < indices.length; idx++) {
            StopPoint orig = master.get(indices[idx]);
            sub.add(new StopPoint(idx, orig.address, orig.latitude, orig.longitude, orig.formattedAddress, orig.characteristicSurfaceFactor, orig.cachedFromGoogle));
        }
        return sub;
    }

    private MatricesData sliceMatricesByIndices(MatricesData full, int[] indices) {
        int subN = indices.length;
        double[][] d = new double[subN][subN];
        double[][] t = new double[subN][subN];
        double[][] s = new double[subN][subN];
        double[][] w = new double[subN][subN];
        for (int i = 0; i < subN; i++) {
            for (int j = 0; j < subN; j++) {
                d[i][j] = full.distanceKmMatrix[indices[i]][indices[j]];
                t[i][j] = full.durationMinMatrix[indices[i]][indices[j]];
                s[i][j] = full.surfaceFactorMatrix[indices[i]][indices[j]];
                w[i][j] = full.weightedCostMatrix[indices[i]][indices[j]];
            }
        }
        return new MatricesData(d, t, s, w);
    }

    private double fetchRouteSurfaceFactorFromOrs(int[] tour, List<StopPoint> stops, String orsApiKey) {
        String tourKey = Arrays.toString(tour);
        if (orsRouteSurfaceCache.containsKey(tourKey)) {
            return orsRouteSurfaceCache.get(tourKey);
        }
        if (orsApiKey == null || orsApiKey.isBlank()) {
            return Double.NaN;
        }
        try {
            StringBuilder coordsJson = new StringBuilder("[");
            for (int i = 0; i < tour.length; i++) {
                if (i > 0) coordsJson.append(",");
                StopPoint p = stops.get(tour[i]);
                coordsJson.append(String.format(Locale.US, "[%.6f,%.6f]", p.longitude, p.latitude));
            }
            coordsJson.append("]");

            String payload = String.format(
                    "{\"coordinates\":%s,\"instructions\":false,\"units\":\"m\",\"extra_info\":[\"surface\",\"waytype\",\"waycategory\",\"tollways\"]}",
                    coordsJson
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ORS_BASE_URL + "/v2/directions/driving-car/geojson"))
                    .header("Authorization", orsApiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            orsDirectionsRequestsCount++;
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                double factor = parseOrsSurfaceSummaryFactor(response.body());
                orsRouteSurfaceCache.put(tourKey, factor);
                return factor;
            }
        } catch (Exception ignored) {
        }
        return Double.NaN;
    }

    public static double factorFor(int surface) {
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

    private RouteMetrics evaluateTour(
            String algorithmName,
            String complexity,
            String category,
            int[] tour,
            double executionTimeMs,
            MatricesData matrices,
            List<StopPoint> stops,
            String orsApiKey,
            String notes) {
        double totalDistKm = tourCost(tour, matrices.distanceKmMatrix);
        double totalDurationMin = tourCost(tour, matrices.durationMinMatrix);
        double totalWeightedCost = tourCost(tour, matrices.weightedCostMatrix);
        double avgSurfaceFactor = totalDistKm > 0 ? (totalWeightedCost / totalDistKm) : 1.0;

        double orsLiveSurface = fetchRouteSurfaceFactorFromOrs(tour, stops, orsApiKey);
        if (!Double.isNaN(orsLiveSurface) && avgSurfaceFactor <= 1.01) {
            avgSurfaceFactor = orsLiveSurface;
            totalWeightedCost = totalDistKm * avgSurfaceFactor;
        }

        return new RouteMetrics(
                algorithmName,
                complexity,
                category,
                tour,
                executionTimeMs,
                totalDistKm,
                totalDurationMin,
                avgSurfaceFactor,
                totalWeightedCost,
                notes
        );
    }

    // =========================================================================
    // IMPRESSÃO DE TABELAS E DIAGNÓSTICO DO ALNS NO CONSOLE
    // =========================================================================
    private void printBanner() {
        System.out.println("##############################################################################################################");
        System.out.println("#  BATERIA CIENTÍFICA CALIBRADA PELO MERCADO DE LOGÍSTICA & DELIVERY (N=12 até N=110 PARADAS/DIA)            #");
        System.out.println("#  Foco Principal da IC: ALNS (Adaptive Large Neighborhood Search) c/ Ponderação de Superfície Viária        #");
        System.out.println("##############################################################################################################");
    }

    private void printMarketResearchHeader() {
        System.out.println("\n  [EVIDÊNCIAS DE MERCADO: MÉDIA DE PARADAS DIÁRIAS POR MOTORISTA NA LOGÍSTICA E DELIVERY]");
        System.out.println("  +---------------------------------------------+-------------------+--------------------+-------------------+");
        System.out.println("  | Segmento Operacional Logístico              | Paradas / Dia (N) | Tempo Atendimento  | Cenário na Bateria|");
        System.out.println("  +---------------------------------------------+-------------------+--------------------+-------------------+");
        System.out.println("  | 1. Carga Pesada / Móveis / B2B Doca         |  10 a  25 paradas | 15 a 25 min/parada | C1 (N = 12)       |");
        System.out.println("  | 2. Logística Portuária / Barreira Geográfica|  15 a  25 paradas | 10 a 15 min/parada | C2 (N = 15)       |");
        System.out.println("  | 3. Zonas Rurais / Estradas Não Pavimentadas |  15 a  30 paradas |  8 a 12 min/parada | C3 (N = 18)       |");
        System.out.println("  | 4. Distribuição Comercial B2B / VUC Regional|  30 a  50 paradas |  6 a 10 min/parada | C4 (N = 35)       |");
        System.out.println("  | 5. E-commerce Last-Mile Brasil (Vans/Fiorino|  60 a  90 paradas |  3 a  5 min/parada | C5 (N = 70)       |");
        System.out.println("  | 6. E-commerce Ultra-Denso / Black Friday    | 100 a 150+ paradas|  2 a  3 min/parada | C6 (N = 110)      |");
        System.out.println("  +---------------------------------------------+-------------------+--------------------+-------------------+");
    }

    private void printScenarioComparisonTable(RouteMetrics baseline, List<RouteMetrics> results, double referenceBestCost) {
        System.out.printf("  %-39s | %-26s | %-12s | %-11s | %-8s | %-8s | %-7s | %-9s | %-9s | %-7s%n",
                "Algoritmo / Modelo", "Categoria", "Complexidade", "Tempo CPU", "Dist(km)", "Rota(m)", "Custo Z", "Econ(km)", "Econ(min)", "Gap(%)");
        System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");

        double baseGap = ((baseline.weightedCost - referenceBestCost) / referenceBestCost) * 100.0;
        System.out.printf(Locale.US, "  %-39s | %-26s | %-12s | %8s ms | %8.2f | %8.1f | %7.2f | %9s | %9s | %6.2f%%%n",
                baseline.algorithmName, baseline.category, baseline.complexity, "---",
                baseline.distanceKm, baseline.durationMin, baseline.weightedCost, "Baseline", "Baseline", baseGap);
        System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");

        for (RouteMetrics r : results) {
            double deltaKm = r.distanceKm - baseline.distanceKm;
            double deltaMin = r.durationMin - baseline.durationMin;
            double gapPercent = ((r.weightedCost - referenceBestCost) / referenceBestCost) * 100.0;

            System.out.printf(Locale.US, "  %-39s | %-26s | %-12s | %8.4f ms | %8.2f | %8.1f | %7.2f | %+6.2f km | %+5.1f min | %6.2f%%%n",
                    r.algorithmName,
                    r.category,
                    r.complexity,
                    r.executionTimeMs,
                    r.distanceKm,
                    r.durationMin,
                    r.weightedCost,
                    deltaKm,
                    deltaMin,
                    Math.max(0.0, gapPercent));
        }
        System.out.println("  ====================================================================================================================================================");
        for (RouteMetrics r : results) {
            System.out.printf(Locale.US, "  * %-39s -> %s%n", r.algorithmName, r.notes);
        }
    }

    private void printAlnsDiagnosticBox(
            List<StopPoint> stops,
            MatricesData matrices,
            RouteMetrics baseline,
            AlnsDetailedResult std,
            AlnsDetailedResult enh,
            double dwellTimeMin,
            boolean isGeographicBarrier) {
        int n = stops.size();
        double totalDwellMin = (n - 1) * dwellTimeMin;
        double baselineShiftHours = (baseline.durationMin + totalDwellMin) / 60.0;
        double alnsShiftHours = (tourCost(enh.bestTour, matrices.durationMinMatrix) + totalDwellMin) / 60.0;

        System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");
        System.out.printf(Locale.US, "  [RAIO-X DO ALNS E IMPACTO NA JORNADA DIÁRIA DO MOTORISTA (%d ENTREGAS)]%n", n);
        System.out.printf(Locale.US, "  - ALNS Padrão (Shaw Geográfico)      : Melhor Z = %7.2f | Média = %7.2f | Pior = %7.2f | Desvio Padrão = %.3f | CPU = %.4f ms%n",
                std.bestCost, std.meanCost, std.worstCost, std.stdDevCost, std.avgTimeMs);
        System.out.printf(Locale.US, "  - ALNS Aperfeiçoado (Shaw Viário+LS) : Melhor Z = %7.2f | Média = %7.2f | Pior = %7.2f | Desvio Padrão = %.3f | CPU = %.4f ms%n",
                enh.bestCost, enh.meanCost, enh.worstCost, enh.stdDevCost, enh.avgTimeMs);
        System.out.printf(Locale.US, "  - Jornada Total (Direção + %.0f min/parada): Sem Otimização = %.1f horas | Com ALNS = %.1f horas (Economia de %.1f horas no turno!)%n",
                dwellTimeMin, baselineShiftHours, alnsShiftHours, (baselineShiftHours - alnsShiftHours));

        if (isGeographicBarrier) {
            double[][] euclidMatrix = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    euclidMatrix[i][j] = haversineKm(stops.get(i).latitude, stops.get(i).longitude, stops.get(j).latitude, stops.get(j).longitude);
                }
            }
            int[] euclidOptimalTour = solveHeldKarpExactDp(euclidMatrix);
            double fakeEuclidKm = tourCost(euclidOptimalTour, euclidMatrix);
            double realRoadKmOfEuclidTour = tourCost(euclidOptimalTour, matrices.distanceKmMatrix);
            double realRoadMinOfEuclidTour = tourCost(euclidOptimalTour, matrices.durationMinMatrix);
            double alnsRealKm = tourCost(enh.bestTour, matrices.distanceKmMatrix);
            double alnsRealMin = tourCost(enh.bestTour, matrices.durationMinMatrix);

            System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");
            System.out.println("  [PROVA DO PARADOXO GEOGRÁFICO: SANTOS x GUARUJÁ (Por que Distância em Linha Reta Falha!)]");
            System.out.printf(Locale.US, "  * Distância em Linha Reta Ponta da Praia (Santos) <-> Vicente de Carvalho (Guarujá): %.2f km | Por Rodovia Real (via Cubatão): %.2f km!%n",
                    euclidMatrix[1][2], matrices.distanceKmMatrix[1][2]);
            System.out.printf(Locale.US, "  * Rota 'Ótima' em Linha Reta (Haversine) acha que tem %.2f km, mas ao dirigir nas rodovias reais percorre %.2f km (%.1f min)!%n",
                    fakeEuclidKm, realRoadKmOfEuclidTour, realRoadMinOfEuclidTour);
            System.out.printf(Locale.US, "  * Rota do ALNS com Matriz Viária ORS percorre apenas %.2f km (%.1f min) -> Economia Real de %.2f km e %.1f min sobre o TSP Geográfico!%n",
                    alnsRealKm, alnsRealMin, (realRoadKmOfEuclidTour - alnsRealKm), (realRoadMinOfEuclidTour - alnsRealMin));
        }
        System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");
    }

    private void printConsolidatedBatteryReport(List<ScenarioSummary> summaries) {
        System.out.println("\n######################################################################################################################################################");
        System.out.println("#  RESUMO CONSOLIDADO DA BATERIA CALIBRADA PELO MERCADO (N=12 até N=110 PARADAS/DIA)                                                                #");
        System.out.println("######################################################################################################################################################");
        System.out.printf("  %-36s | %-4s | %-9s | %-9s | %-7s | %-7s | %-7s | %-8s | %-8s | %-11s | %-11s%n",
                "Cenário de Mercado", "N", "Baseline", "Ótimo(km)", "Gap NN", "Gap2opt", "Gap3opt", "GapALNS0", "GapALNS+", "CPU 3-opt", "CPU ALNS+");
        System.out.println("  ----------------------------------------------------------------------------------------------------------------------------------------------------");
        for (ScenarioSummary s : summaries) {
            System.out.printf(Locale.US, "  %-36s | %4d | %6.1f km | %6.2f km | %6.2f%% | %6.2f%% | %6.2f%% | %7.2f%% | %7.2f%% | %8.3f ms | %8.3f ms%n",
                    s.title.length() > 36 ? s.title.substring(0, 36) : s.title,
                    s.n,
                    s.baselineKm,
                    s.bestDistKm,
                    Math.max(0.0, s.nnGap),
                    Math.max(0.0, s.twoOptGap),
                    Math.max(0.0, s.lkhGap),
                    Math.max(0.0, s.alnsStdGap),
                    Math.max(0.0, s.alnsEnhGap),
                    s.lkhCpuMs,
                    s.alnsEnhCpuMs
            );
        }
        System.out.println("######################################################################################################################################################");
    }

    private void printApiConsumptionReport() {
        System.out.println("\n----------------------------------------------------------------------------------------------------------");
        System.out.println("  [RELATÓRIO DE CONSUMO REAL DE APIS NESTA BATERIA DE MERCADO]");
        System.out.printf("  - Requisições ao Google Maps Geocoding API : %d%n", googleRequestsCount);
        System.out.printf("  - Requisições ao ORS Matrix API (/matrix)  : %d (1 matriz 35x35 + 1 matriz 15x15)%n", orsMatrixRequestsCount);
        System.out.printf("  - Requisições ao ORS Directions (/geojson) : %d%n", orsDirectionsRequestsCount);
        System.out.println("----------------------------------------------------------------------------------------------------------\n");
    }

    // =========================================================================
    // AUXILIARES GEODÉSICOS, PARSERS JSON E ESTRUTURAS
    // =========================================================================
    private void fillFallbackCampinasMatrices(
            List<StopPoint> stops,
            double[][] distanceKm,
            double[][] durationMin,
            double[][] surfaceFactors) {
        int n = stops.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double haversine = haversineKm(stops.get(i).latitude, stops.get(i).longitude, stops.get(j).latitude, stops.get(j).longitude);
                double tortuosity = 1.32 + ((i > j) ? 0.05 : 0.0);
                distanceKm[i][j] = haversine * tortuosity;
                durationMin[i][j] = (distanceKm[i][j] / 28.0) * 60.0 + 1.1;
            }
        }
    }

    private static void reverseSubSegment(int[] tour, int left, int right) {
        while (left < right) {
            int temp = tour[left];
            tour[left] = tour[right];
            tour[right] = temp;
            left++;
            right--;
        }
    }

    private static int[] tournamentSelect(List<int[]> pop, double[][] costMatrix, Random rng) {
        int[] best = pop.get(rng.nextInt(pop.size()));
        double bestCost = tourCost(best, costMatrix);
        for (int i = 0; i < 3; i++) {
            int[] contender = pop.get(rng.nextInt(pop.size()));
            double cCost = tourCost(contender, costMatrix);
            if (cCost < bestCost) {
                best = contender;
                bestCost = cCost;
            }
        }
        return best;
    }

    private static int[] orderCrossover(int[] p1, int[] p2, int n, Random rng) {
        int[] child = new int[n + 1];
        Arrays.fill(child, -1);
        child[0] = p1[0];
        child[n] = p1[0];

        int start = 1 + rng.nextInt(n - 1);
        int end = start + rng.nextInt(n - start);
        boolean[] used = new boolean[n];
        used[p1[0]] = true;

        for (int i = start; i <= end; i++) {
            child[i] = p1[i];
            used[child[i]] = true;
        }
        int pos = 1;
        for (int i = 1; i < n; i++) {
            int gene = p2[i];
            if (!used[gene]) {
                while (pos >= start && pos <= end) pos++;
                child[pos++] = gene;
                used[gene] = true;
            }
        }
        return child;
    }

    private static double[] parseGoogleGeocodeLatLng(String json, double defaultLat, double defaultLng) {
        Pattern locPattern = Pattern.compile("\"location\"\\s*:\\s*\\{\\s*\"lat\"\\s*:\\s*([-0-9.]+)\\s*,\\s*\"lng\"\\s*:\\s*([-0-9.]+)");
        Matcher m = locPattern.matcher(json);
        if (m.find()) {
            return new double[]{ Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
        }
        return new double[]{ defaultLat, defaultLng };
    }

    private static String parseGoogleFormattedAddress(String json, String defaultAddr) {
        Pattern addrPattern = Pattern.compile("\"formatted_address\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = addrPattern.matcher(json);
        return m.find() ? m.group(1) : defaultAddr;
    }

    private static double[][] parse2dMatrixFromJson(String json, String fieldName, int n) {
        double[][] matrix = new double[n][n];
        int keyIdx = json.indexOf("\"" + fieldName + "\"");
        if (keyIdx < 0) return matrix;
        int startBracket = json.indexOf("[[", keyIdx);
        int endBracket = json.indexOf("]]", startBracket);
        if (startBracket < 0 || endBracket < 0) return matrix;

        String content = json.substring(startBracket + 2, endBracket);
        String[] rows = content.split("\\]\\s*,\\s*\\[");
        for (int i = 0; i < Math.min(n, rows.length); i++) {
            String[] cols = rows[i].split(",");
            for (int j = 0; j < Math.min(n, cols.length); j++) {
                matrix[i][j] = Double.parseDouble(cols[j].trim());
            }
        }
        return matrix;
    }

    private static double parseOrsSurfaceSummaryFactor(String json) {
        int surfaceIdx = json.indexOf("\"surface\"");
        if (surfaceIdx < 0) return 1.0;
        int summaryIdx = json.indexOf("\"summary\"", surfaceIdx);
        if (summaryIdx < 0) return 1.0;
        int arrayStart = json.indexOf("[", summaryIdx);
        int arrayEnd = json.indexOf("]", arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) return 1.0;

        String summaryBlock = json.substring(arrayStart, arrayEnd + 1);
        Pattern itemPattern = Pattern.compile("\\{[^}]*\"value\"\\s*:\\s*([0-9.]+)[^}]*\"distance\"\\s*:\\s*([0-9.]+)[^}]*\\}|\\{[^}]*\"distance\"\\s*:\\s*([0-9.]+)[^}]*\"value\"\\s*:\\s*([0-9.]+)[^}]*\\}");
        Matcher m = itemPattern.matcher(summaryBlock);

        double weightedFactors = 0.0;
        double totalDistance = 0.0;
        while (m.find()) {
            double val = m.group(1) != null ? Double.parseDouble(m.group(1)) : Double.parseDouble(m.group(4));
            double dist = m.group(2) != null ? Double.parseDouble(m.group(2)) : Double.parseDouble(m.group(3));
            weightedFactors += dist * factorFor((int) Math.round(val));
            totalDistance += dist;
        }
        return totalDistance == 0.0 ? 1.0 : Math.max(1.0, weightedFactors / totalDistance);
    }

    private static String resolveKey(String envName, String fallbackConstant) {
        String envVal = System.getenv(envName);
        if (envVal != null && !envVal.isBlank()) return envVal.trim();
        return fallbackConstant == null ? "" : fallbackConstant.trim();
    }

    private static int[] createBaselineTour(int n) {
        int[] tour = new int[n + 1];
        for (int i = 0; i < n; i++) tour[i] = i;
        tour[n] = 0;
        return tour;
    }

    private static int[] randomTour(int n, int startNode, Random rng) {
        int[] tour = new int[n + 1];
        tour[0] = startNode;
        tour[n] = startNode;
        List<Integer> middle = new ArrayList<>(n - 1);
        for (int i = 0; i < n; i++) {
            if (i != startNode) middle.add(i);
        }
        for (int i = middle.size() - 1; i > 0; i--) {
            int swapIdx = rng.nextInt(i + 1);
            int tmp = middle.get(i);
            middle.set(i, middle.get(swapIdx));
            middle.set(swapIdx, tmp);
        }
        for (int i = 0; i < middle.size(); i++) tour[i + 1] = middle.get(i);
        return tour;
    }

    private static double tourCost(int[] tour, double[][] matrix) {
        double sum = 0.0;
        for (int i = 0; i < tour.length - 1; i++) sum += matrix[tour[i]][tour[i + 1]];
        return sum;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static long factorial(int k) {
        long res = 1L;
        for (int i = 2; i <= Math.min(k, 20); i++) res *= i;
        return res;
    }

    private static double factorialDouble(int k) {
        double res = 1.0;
        for (int i = 2; i <= k; i++) res *= i;
        return res;
    }

    private record StopPoint(
            int id,
            String address,
            double latitude,
            double longitude,
            String formattedAddress,
            double characteristicSurfaceFactor,
            boolean cachedFromGoogle
    ) {}

    private record MatricesData(
            double[][] distanceKmMatrix,
            double[][] durationMinMatrix,
            double[][] surfaceFactorMatrix,
            double[][] weightedCostMatrix
    ) {}

    private record EcommerceClusterInstance(List<StopPoint> stops, MatricesData matrices) {}

    private record ExactResult(int[] tour, double avgTimeMs, long nodesVisited, long branchesPruned, boolean truncated) {}

    private record NnResult(int[] tour, double avgTimeMs, int prunedEvaluations, int totalPossibleEvaluations) {}

    private record AlgoResult(int[] tour, double avgTimeMs) {}

    private record AlnsRunTrace(int[] bestTour, double bestCost, double[] finalDestroyWeights, double[] finalRepairWeights) {}

    private record AlnsDetailedResult(
            int[] bestTour,
            double avgTimeMs,
            double bestCost,
            double meanCost,
            double worstCost,
            double stdDevCost,
            double[] avgDestroyWeights,
            double[] avgRepairWeights
    ) {}

    private record RouteMetrics(
            String algorithmName,
            String complexity,
            String category,
            int[] tour,
            double executionTimeMs,
            double distanceKm,
            double durationMin,
            double surfaceFactor,
            double weightedCost,
            String notes
    ) {}

    private record ScenarioSummary(
            String title,
            int n,
            double baselineKm,
            double baselineMin,
            double bestWeightedCost,
            String bestQualityAlgo,
            double bestDistKm,
            double bestDurationMin,
            double bestAlgoCpuMs,
            double nnGap,
            double twoOptGap,
            double lkhGap,
            double alnsStdGap,
            double alnsEnhGap,
            double alnsStdCpuMs,
            double alnsEnhCpuMs,
            double lkhCpuMs,
            double memeticCpuMs,
            String fastestAlgo,
            double fastestCpuMs
    ) {}
}
