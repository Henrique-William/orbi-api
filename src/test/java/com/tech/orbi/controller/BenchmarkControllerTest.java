package com.tech.orbi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.orbi.Repository.BenchmarkRepository;
import com.tech.orbi.dto.BenchmarkAddressDto;
import com.tech.orbi.dto.BenchmarkRunRequestDto;
import com.tech.orbi.dto.BenchmarkSessionSummaryDto;
import com.tech.orbi.entity.Benchmark;
import com.tech.orbi.service.BenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenchmarkControllerTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    private BenchmarkService benchmarkService;
    private BenchmarkController benchmarkController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        benchmarkService = new BenchmarkService(benchmarkRepository, objectMapper);
        benchmarkController = new BenchmarkController(benchmarkService);
    }

    @Test
    void testRunBenchmarkWithInlineAddresses() {
        List<BenchmarkAddressDto> points = List.of(
                new BenchmarkAddressDto(0, "Ponto A", -22.90, -47.06, "Rua A", "Rua A", 1.0, true),
                new BenchmarkAddressDto(1, "Ponto B", -22.91, -47.05, "Rua B", "Rua B", 1.0, true),
                new BenchmarkAddressDto(2, "Ponto C", -22.89, -47.04, "Rua C", "Rua C", 1.05, true),
                new BenchmarkAddressDto(3, "Ponto D", -22.92, -47.07, "Rua D", "Rua D", 1.0, true)
        );

        BenchmarkRunRequestDto request = new BenchmarkRunRequestDto(
                "Teste Customizado",
                "Cenário de teste com 4 pontos",
                points,
                null,
                4,
                3.0,
                true,
                true
        );

        when(benchmarkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = benchmarkController.runBenchmark(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof BenchmarkSessionSummaryDto);
        BenchmarkSessionSummaryDto summary = (BenchmarkSessionSummaryDto) response.getBody();
        assertNotNull(summary);
        assertEquals("Teste Customizado", summary.scenarioName());
        assertEquals(4, summary.stopsCount());
        assertTrue(summary.initialDurationMin() > 0);
        assertTrue(summary.initialDistanceKm() > 0);
        assertNotNull(summary.models());
        assertFalse(summary.models().isEmpty());

        // Verifica que o tempo de execução é computacional puro e positivo
        summary.models().forEach(m -> {
            assertNotNull(m.executionTimeMs());
            assertTrue(m.executionTimeMs() >= 0.0);
            assertNotNull(m.totalDistanceKm());
            assertTrue(m.totalDistanceKm() > 0.0);
        });

        // Verifica que salvou no repositório MySQL
        verify(benchmarkRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testRunBenchmarkDefaultPoolFromClasspathJson() {
        when(benchmarkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        BenchmarkRunRequestDto request = new BenchmarkRunRequestDto(
                "C1: CARGA PESADA",
                "Teste com pool padrão do benchmark_addresses.json",
                null,
                null,
                10,
                5.0,
                true,
                true
        );

        ResponseEntity<?> response = benchmarkController.runBenchmark(request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof BenchmarkSessionSummaryDto);
        BenchmarkSessionSummaryDto summary = (BenchmarkSessionSummaryDto) response.getBody();
        assertNotNull(summary);
        assertEquals(10, summary.stopsCount());
        assertTrue(summary.models().stream().anyMatch(m -> m.algorithmName().contains("ALNS")));
        assertTrue(summary.models().stream().anyMatch(m -> m.algorithmName().contains("Baseline")));
        assertTrue(summary.models().stream().anyMatch(m -> m.algorithmName().contains("Branch-and-Bound")));
    }

    @Test
    void testRunAllScenariosInSequence() {
        when(benchmarkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<com.tech.orbi.dto.BenchmarkBatchSummaryDto> response = benchmarkController.runAllScenarios();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        com.tech.orbi.dto.BenchmarkBatchSummaryDto batch = response.getBody();
        assertNotNull(batch);
        assertNotNull(batch.batchId());
        assertEquals(6, batch.totalScenarios());
        assertEquals(6, batch.scenarioSessions().size());
        assertTrue(batch.overallMaxSavingsPct() > 0);
        // Verifica que todos os 6 cenários (C1..C6) foram executados e vinculados ao mesmo batchId
        for (BenchmarkSessionSummaryDto session : batch.scenarioSessions()) {
            assertEquals(batch.batchId(), session.batchId());
            assertNotNull(session.scenarioCode());
            assertNotNull(session.models());
            assertFalse(session.models().isEmpty());

            // Todos os algoritmos dentro do cenário compartilham exatamente os mesmos critérios de entrada
            double refInitialDist = session.initialDistanceKm();
            int refStops = session.stopsCount();
            System.out.println("=== CENÁRIO " + session.scenarioCode() + " (N=" + refStops + ", Baseline=" + String.format("%.2f", refInitialDist) + " km, Best=" + session.bestAlgorithm() + ") ===");
            for (var m : session.models()) {
                assertEquals(refStops, m.stopsCount());
                assertEquals(refInitialDist, m.initialDistanceKm(), 1e-6);
                System.out.printf(java.util.Locale.US, "  %-38s | dist=%7.2f km | cost=%7.2f | cpu=%7.3f ms%n",
                        m.algorithmName(), m.totalDistanceKm(), m.totalCost(), m.executionTimeMs());
            }
        }
        verify(benchmarkRepository, times(6)).saveAll(anyList());
    }

    @Test
    void testGetAddresses() {
        ResponseEntity<List<BenchmarkAddressDto>> response = benchmarkController.getAddresses(null);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        List<BenchmarkAddressDto> list = response.getBody();
        assertNotNull(list);
        assertFalse(list.isEmpty());
        // Deve carregar os pontos do arquivo com coordenadas do Google
        BenchmarkAddressDto first = list.get(0);
        assertNotNull(first.latitude());
        assertNotNull(first.longitude());
        assertTrue(first.latitude() < 0);
        assertTrue(first.longitude() < 0);
    }

    @Test
    void testGetLatestBatchConsolidatesAllSixScenariosFromDb() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // Simula lote mais recente contendo C6 salvo por último e sessões C1..C5 com ou sem scenarioCode explícito
        Benchmark b6 = createEntity(60L, "sess-6", "batch-partial", null, "C6: Alta Densidade (N = 110)", 110, "9. ALNS Orbi (Ponderação Viária)", 284.8, 571.8, now);
        Benchmark b1 = createEntity(10L, "sess-1", "batch-old", null, "Carga Pesada B2B", 12, "9. ALNS Orbi (Ponderação Viária)", 35.06, 46.74, now.minusMinutes(5));
        Benchmark b2 = createEntity(20L, "sess-2", null, "c2", "Barreira Santos x Guarujá", 15, "9. ALNS Orbi (Ponderação Viária)", 108.28, 568.42, now.minusMinutes(4));
        Benchmark b3 = createEntity(30L, "sess-3", "batch-old", "C3", "C3: Logística Rural", 18, "9. ALNS Orbi (Ponderação Viária)", 119.57, 218.54, now.minusMinutes(3));
        Benchmark b4 = createEntity(40L, "sess-4", "batch-old", null, "Distribuição Comercial VUC", 35, "9. ALNS Orbi (Ponderação Viária)", 238.19, 442.26, now.minusMinutes(2));
        Benchmark b5 = createEntity(50L, "sess-5", null, null, "E-commerce Last-Mile", 70, "9. ALNS Orbi (Ponderação Viária)", 262.21, 512.10, now.minusMinutes(1));

        when(benchmarkRepository.findDistinctBatchIds()).thenReturn(List.of("batch-partial"));
        when(benchmarkRepository.findByBatchIdOrderByIdAsc("batch-partial")).thenReturn(List.of(b6));
        when(benchmarkRepository.findAllByOrderByIdDesc()).thenReturn(List.of(b6, b5, b4, b3, b2, b1));

        ResponseEntity<com.tech.orbi.dto.BenchmarkBatchSummaryDto> response = benchmarkController.getLatestBatch();
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        com.tech.orbi.dto.BenchmarkBatchSummaryDto batch = response.getBody();
        assertNotNull(batch);
        assertEquals(6, batch.totalScenarios());
        assertEquals(6, batch.scenarioSessions().size());

        List<String> codes = batch.scenarioSessions().stream()
                .map(BenchmarkSessionSummaryDto::scenarioCode)
                .toList();
        assertEquals(List.of("C1", "C2", "C3", "C4", "C5", "C6"), codes);
    }

    private Benchmark createEntity(
            Long id,
            String sessionId,
            String batchId,
            String scenarioCode,
            String scenarioName,
            int stopsCount,
            String algoName,
            double distKm,
            double initialDistKm,
            java.time.LocalDateTime createdAt
    ) {
        Benchmark b = new Benchmark();
        b.setId(id);
        b.setSessionId(sessionId);
        b.setBatchId(batchId);
        b.setScenarioCode(scenarioCode);
        b.setScenarioName(scenarioName);
        b.setStopsCount(stopsCount);
        b.setAlgorithmName(algoName);
        b.setAlgorithmType("Meta-heurística");
        b.setComplexity("O(I * n^2)");
        b.setExecutionTimeMs(1.5);
        b.setTotalDistanceKm(distKm);
        b.setTotalDurationMin(200.0);
        b.setTotalCost(distKm);
        b.setAvgSurfaceFactor(1.0);
        b.setEfficiencyGainPct(((initialDistKm - distKm) / initialDistKm) * 100.0);
        b.setInitialDurationMin(400.0);
        b.setInitialDistanceKm(initialDistKm);
        b.setRouteCoordinatesJson("[]");
        b.setCreatedAt(createdAt);
        return b;
    }
}
