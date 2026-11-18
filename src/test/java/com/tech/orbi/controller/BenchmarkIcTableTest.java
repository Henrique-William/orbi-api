package com.tech.orbi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tech.orbi.Repository.BenchmarkRepository;
import com.tech.orbi.dto.BenchmarkBatchSummaryDto;
import com.tech.orbi.dto.BenchmarkResultDto;
import com.tech.orbi.dto.BenchmarkRunRequestDto;
import com.tech.orbi.dto.BenchmarkSessionSummaryDto;
import com.tech.orbi.service.BenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Suíte de Testes Automatizados baseada na Tabela 2 do Relatório da Iniciação Científica (IC).
 * Valida formalmente:
 * 1. O Conceito da Métrica de Gap de Otimalidade (Gap (%) = ((Z_alg - Z*) / Z*) * 100%).
 * 2. A Presença e Desempenho do 3-opt / Lin-Kernighan (LKH) nos cenários.
 * 3. As Proteções Operacionais:
 *    - Truncamento seguro de Branch-and-Bound em 3.000.000 nós visitados (evita travamento O((n-1)!)).
 *    - Salvaguarda contra colapso teórico de memória RAM no Bellman-Held-Karp (N >= 35, evita OOM).
 * 4. A bateria de 6 cenários calibrados pelo mercado (C1, C2, C3, C4, C5 e C6).
 */
@ExtendWith(MockitoExtension.class)
class BenchmarkIcTableTest {

    @Mock
    private BenchmarkRepository benchmarkRepository;

    private BenchmarkService benchmarkService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        benchmarkService = new BenchmarkService(benchmarkRepository, objectMapper);
        when(benchmarkRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("C1 (N=12) - Carga Pesada B2B: Valida Métodos Exatos (BHK/B&B), LKH e Gap de Otimalidade")
    void testC1CargaPesadaExactMethodsAndGap() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C1: Carga Pesada / B2B Doca (N = 12)",
                "Entregas pesadas com 18 min de doca por parada",
                null,
                "c1.json",
                12,
                18.0,
                true,
                true,
                false,
                "batch-c1-test",
                "C1"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertNotNull(session);
        assertEquals("C1", session.scenarioCode());
        assertEquals(12, session.stopsCount());

        List<BenchmarkResultDto> models = session.models();
        assertFalse(models.isEmpty());

        // 1. Conceito da Métrica: Gap de Otimalidade
        BenchmarkResultDto winner = models.stream()
                .filter(m -> !m.algorithmName().contains("Baseline") && m.totalCost() > 0)
                .min((a, b) -> Double.compare(a.totalCost(), b.totalCost()))
                .orElseThrow();

        // O vencedor deve ter Gap de 0.00%
        assertNotNull(winner.optimalityGapPct());
        assertEquals(0.0, winner.optimalityGapPct(), 1e-4, "O vencedor deve possuir Gap de otimalidade igual a 0.00%");

        // A baseline deve ter gap expressivo
        BenchmarkResultDto baseline = models.stream()
                .filter(m -> m.algorithmName().contains("Baseline"))
                .findFirst().orElseThrow();
        assertNotNull(baseline.optimalityGapPct());
        assertTrue(baseline.optimalityGapPct() > 20.0, "Baseline em C1 deve apresentar Gap superior a 20%");

        // 2. Presença do LKH (3-opt)
        BenchmarkResultDto lkh = models.stream()
                .filter(m -> m.algorithmName().contains("3-opt / Lin-Kernighan"))
                .findFirst().orElseThrow();
        assertEquals("O(n^3)", lkh.complexity());
        assertNotNull(lkh.optimalityGapPct());
        assertTrue(lkh.optimalityGapPct() <= 1.0, "3-opt LKH deve atingir rota ótima ou quase ótima em N=12");

        // 3. Métodos Exatos em N=12
        BenchmarkResultDto hk = models.stream()
                .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                .findFirst().orElseThrow();
        assertEquals("Exato", hk.algorithmType());
        assertEquals(0.0, hk.optimalityGapPct(), 1e-4, "Held-Karp deve atingir o ótimo exato (0.00%) em N=12");

        BenchmarkResultDto bb = models.stream()
                .filter(m -> m.algorithmName().contains("Branch-and-Bound"))
                .findFirst().orElseThrow();
        assertTrue(bb.algorithmType().startsWith("Exato"));
        assertTrue(bb.executionTimeMs() >= 0.0);
    }

    @Test
    @DisplayName("C2 (N=15) - Barreira Santos x Guarujá: Valida LKH, Gap e Desempenho")
    void testC2SantosGuarujaBarrierAndLkh() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C2: Barreira Geográfica Santos x Guarujá (N = 15)",
                "Cargas portuárias e canal estuarino",
                null,
                "c2.json",
                15,
                12.0,
                true,
                true,
                false,
                "batch-c2-test",
                "C2"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertNotNull(session);
        assertEquals("C2", session.scenarioCode());
        assertEquals(15, session.stopsCount());

        List<BenchmarkResultDto> models = session.models();

        // Baseline com gap monumental decorrente de travessias cegas
        BenchmarkResultDto baseline = models.stream()
                .filter(m -> m.algorithmName().contains("Baseline"))
                .findFirst().orElseThrow();
        assertTrue(baseline.optimalityGapPct() > 100.0, "C2 deve registrar desvio superior a 100% na baseline");

        // Presença e eficiência do LKH
        BenchmarkResultDto lkh = models.stream()
                .filter(m -> m.algorithmName().contains("3-opt / Lin-Kernighan"))
                .findFirst().orElseThrow();
        assertTrue(lkh.efficiencyGainPct() > 70.0, "LKH deve gerar mais de 70% de economia em C2");
        assertTrue(lkh.optimalityGapPct() < 5.0, "Gap do LKH deve ser restrito");
    }

    @Test
    @DisplayName("C3 (N=18) - Logística Rural: Fronteira dos Métodos Exatos (BHK N=18)")
    void testC3RuralRoadExactFrontier() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C3: Logística Rural e Vias de Terra (N = 18)",
                "Sousas e Joaquim Egídio com fatores de terra",
                null,
                "c3.json",
                18,
                10.0,
                true,
                true,
                false,
                "batch-c3-test",
                "C3"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertEquals(18, session.stopsCount());

        // Held-Karp na fronteira máxima permitida de N=18
        BenchmarkResultDto hk = session.models().stream()
                .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                .findFirst().orElseThrow();
        assertEquals("Exato", hk.algorithmType());
        assertTrue(hk.notes().contains("estados DP"));
        assertEquals(0.0, hk.optimalityGapPct(), 1e-4);
    }

    @Test
    @DisplayName("C4 (N=35) - VUC Comercial: Valida Proteção Operacional de Memória (BHK) e Truncamento (B&B)")
    void testC4VucOperationalProtection() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C4: Distribuição Comercial B2B / VUC Regional (N = 35)",
                "35 paradas em Campinas e RMC",
                null,
                "c4.json",
                35,
                7.0,
                true,
                true,
                false,
                "batch-c4-test",
                "C4"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertEquals(35, session.stopsCount());

        List<BenchmarkResultDto> models = session.models();

        // 1. Proteção Operacional no Bellman-Held-Karp (N=35)
        // Deve invalidar o resultado pois a memória RAM estourou
        BenchmarkResultDto hk = models.stream()
                .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                .findFirst().orElseThrow();
        assertEquals("Exato (Proteção Operacional)", hk.algorithmType());
        assertFalse(hk.isValid(), "Held-Karp deve ser invalidado por estouro de RAM em N >= 35");
        assertEquals("INVALID_MEMORY_LIMIT", hk.status());
        assertNull(hk.totalDistanceKm(), "Distância deve ser nula para não computar 0.0 km falsos");
        assertNull(hk.executionTimeMs(), "Tempo de CPU deve ser nulo pois não executou");
        assertNull(hk.optimalityGapPct(), "Gap deve ser nulo");
        assertNotNull(hk.invalidReason());
        assertTrue(hk.invalidReason().contains("memória RAM"));
        assertTrue(hk.notes().contains("Colapso") || hk.notes().contains("INVALIDADO"));

        // 2. Proteção Operacional no Branch-and-Bound (Truncamento seguro em 3.000.000 nós)
        BenchmarkResultDto bb = models.stream()
                .filter(m -> m.algorithmName().contains("Branch-and-Bound"))
                .findFirst().orElseThrow();
        assertTrue(bb.notes().contains("*Truncado em"), "Branch-and-Bound deve alertar truncamento seguro");
        assertEquals("Exato (Truncado)", bb.algorithmType());
        assertEquals("TRUNCATED", bb.status());
        assertTrue(bb.executionTimeMs() < 5000.0, "Truncamento seguro deve responder em milissegundos sem congelar");

        // 3. Presença do 3-opt / Lin-Kernighan (LKH)
        BenchmarkResultDto lkh = models.stream()
                .filter(m -> m.algorithmName().contains("3-opt / Lin-Kernighan"))
                .findFirst().orElseThrow();
        assertNotNull(lkh.optimalityGapPct());
        assertTrue(lkh.optimalityGapPct() <= 1.0, "LKH deve ter gap extremamente baixo em C4");
    }

    @Test
    @DisplayName("C5 (N=70) - E-commerce Last-Mile Vans: Valida Robustez e Gaps em Média Escala")
    void testC5EcommerceLastMileVans() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C5: E-commerce Last-Mile Padrão Brasil (N = 70)",
                "70 paradas de van",
                null,
                "c5.json",
                70,
                4.0,
                true,
                true,
                false,
                "batch-c5-test",
                "C5"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertEquals(70, session.stopsCount());

        List<BenchmarkResultDto> models = session.models();

        // Bellman-Held-Karp salvaguarda
        BenchmarkResultDto hk = models.stream()
                .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                .findFirst().orElseThrow();
        assertEquals("Exato (Proteção Operacional)", hk.algorithmType());

        // LKH 3-opt
        BenchmarkResultDto lkh = models.stream()
                .filter(m -> m.algorithmName().contains("3-opt / Lin-Kernighan"))
                .findFirst().orElseThrow();
        assertTrue(lkh.totalDistanceKm() > 0.0);
        assertTrue(lkh.efficiencyGainPct() > 45.0, "LKH deve cortar quase metade da quilometragem da baseline em C5");

        // ALNS Orbi
        BenchmarkResultDto alns = models.stream()
                .filter(m -> m.algorithmName().contains("ALNS Orbi"))
                .findFirst().orElseThrow();
        assertTrue(alns.optimalityGapPct() < 5.0, "ALNS Orbi deve manter gap estrito");
    }

    @Test
    @DisplayName("C6 (N=110) - Alta Densidade Black Friday: Valida Escala Extrema, Salvaguardas e LKH")
    void testC6AltaDensidadeBlackFridayScale() {
        BenchmarkRunRequestDto req = new BenchmarkRunRequestDto(
                "C6: E-commerce Alta Densidade / Black Friday (N = 110)",
                "110 paradas padrão Amazon DSP",
                null,
                "c6.json",
                110,
                3.0,
                true,
                true,
                false,
                "batch-c6-test",
                "C6"
        );

        BenchmarkSessionSummaryDto session = benchmarkService.runBenchmark(req);
        assertEquals(110, session.stopsCount());

        List<BenchmarkResultDto> models = session.models();

        // Held-Karp não trava e reporta estimativa teórica
        BenchmarkResultDto hk = models.stream()
                .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                .findFirst().orElseThrow();
        assertEquals("Exato (Proteção Operacional)", hk.algorithmType());
        assertTrue(hk.notes().contains("GB RAM") || hk.notes().contains("TB RAM"));

        // Branch-and-Bound protegido
        BenchmarkResultDto bb = models.stream()
                .filter(m -> m.algorithmName().contains("Branch-and-Bound"))
                .findFirst().orElseThrow();
        assertEquals("Exato (Truncado)", bb.algorithmType());

        // LKH 3-opt
        BenchmarkResultDto lkh = models.stream()
                .filter(m -> m.algorithmName().contains("3-opt / Lin-Kernighan"))
                .findFirst().orElseThrow();
        assertEquals("O(n^3)", lkh.complexity());
        assertTrue(lkh.executionTimeMs() > 0.0);

        // Gap de otimalidade das metaheurísticas no padrão do relatório (< 5%)
        BenchmarkResultDto memetic = models.stream()
                .filter(m -> m.algorithmName().contains("Memético"))
                .findFirst().orElseThrow();
        assertNotNull(memetic.optimalityGapPct());
        assertTrue(memetic.optimalityGapPct() < 5.0);
    }

    @Test
    @DisplayName("Bateria Completa da IC: Executa Sequência C1..C6 e Valida Padrão Integrado da Tabela")
    void testAllSixScenariosSequenceFollowsIcReportTableStandard() {
        BenchmarkBatchSummaryDto batch = benchmarkService.runAllScenariosInSequence();
        assertNotNull(batch);
        assertEquals(6, batch.totalScenarios());
        assertEquals(6, batch.scenarioSessions().size());

        List<String> expectedCodes = List.of("C1", "C2", "C3", "C4", "C5", "C6");
        List<String> actualCodes = batch.scenarioSessions().stream()
                .map(BenchmarkSessionSummaryDto::scenarioCode)
                .toList();
        assertEquals(expectedCodes, actualCodes);

        for (BenchmarkSessionSummaryDto s : batch.scenarioSessions()) {
            assertNotNull(s.scenarioCode());
            assertNotNull(s.bestAlgorithm());
            assertTrue(s.maxSavingsPct() > 20.0, "Todos os cenários devem atingir mais de 20% de economia");

            // Valida que TODOS os cenários contêm o LKH 3-opt
            assertTrue(s.models().stream().anyMatch(m -> m.algorithmName().contains("3-opt / Lin-Kernighan")),
                    "O algoritmo LKH (3-opt) deve estar presente no cenário " + s.scenarioCode());

            // Valida presença de métrica Gap de Otimalidade em modelos válidos
            for (BenchmarkResultDto m : s.models()) {
                if (Boolean.TRUE.equals(m.isValid()) && !m.algorithmName().contains("Baseline") && m.totalCost() != null && m.totalCost() > 0) {
                    assertNotNull(m.optimalityGapPct(), "Modelo " + m.algorithmName() + " deve possuir gap preenchido");
                    assertTrue(m.optimalityGapPct() >= 0.0, "Gap deve ser não-negativo");
                }
            }

            // Valida proteção operacional em N >= 35
            if (s.stopsCount() >= 35) {
                BenchmarkResultDto hk = s.models().stream()
                        .filter(m -> m.algorithmName().contains("Bellman-Held-Karp"))
                        .findFirst().orElseThrow();
                assertEquals("Exato (Proteção Operacional)", hk.algorithmType());
                assertFalse(hk.isValid(), "Held-Karp deve ser invalidado em N >= 35 por estouro de memória RAM");
                assertEquals("INVALID_MEMORY_LIMIT", hk.status());
                assertNull(hk.totalDistanceKm(), "Distância deve ser nula para não poluir ranking");
                assertNull(hk.executionTimeMs(), "Tempo de execução deve ser nulo");

                BenchmarkResultDto bb = s.models().stream()
                        .filter(m -> m.algorithmName().contains("Branch-and-Bound"))
                        .findFirst().orElseThrow();
                assertEquals("Exato (Truncado)", bb.algorithmType());
                assertEquals("TRUNCATED", bb.status());
            }
        }

        // Valida que salvou as 6 sessões no repositório MySQL
        verify(benchmarkRepository, times(6)).saveAll(anyList());
    }
}
