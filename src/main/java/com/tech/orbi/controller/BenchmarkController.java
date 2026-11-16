package com.tech.orbi.controller;

import com.tech.orbi.dto.BenchmarkAddressDto;
import com.tech.orbi.dto.BenchmarkBatchSummaryDto;
import com.tech.orbi.dto.BenchmarkRunRequestDto;
import com.tech.orbi.dto.BenchmarkSessionSummaryDto;
import com.tech.orbi.service.BenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * Executa a bateria de benchmark multi-modelos.
     * Pode receber uma lista externa de endereços JSON ou caminhos de arquivo,
     * ou rodar a bateria calibrada padrão.
     *
     * Se runAllScenarios for true, executa todos os 6 cenários (C1..C6) em sequência.
     *
     * Salva os resultados na nova tabela 'benchmark' do MySQL.
     * O tempo medido para cada algoritmo reflete apenas sua execução computacional pura.
     */
    @PostMapping("/run")
    public ResponseEntity<?> runBenchmark(
            @RequestBody(required = false) BenchmarkRunRequestDto request) {
        if (request != null && Boolean.TRUE.equals(request.runAllScenarios())) {
            BenchmarkBatchSummaryDto batch = benchmarkService.runAllScenariosInSequence();
            return ResponseEntity.ok(batch);
        }
        BenchmarkSessionSummaryDto summary = benchmarkService.runBenchmark(request);
        return ResponseEntity.ok(summary);
    }

    /**
     * Executa todos os 6 cenários da IC (C1, C2, C3, C4, C5 e C6) em sequência.
     * Salva os resultados de cada cenário no MySQL vinculados por um batchId comum.
     */
    @PostMapping("/run-all")
    public ResponseEntity<BenchmarkBatchSummaryDto> runAllScenarios() {
        BenchmarkBatchSummaryDto batch = benchmarkService.runAllScenariosInSequence();
        return ResponseEntity.ok(batch);
    }

    /**
     * Retorna a lista de todos os lotes de execução completa (batches) com os 6 cenários.
     */
    @GetMapping("/batches")
    public ResponseEntity<List<BenchmarkBatchSummaryDto>> getAllBatches() {
        List<BenchmarkBatchSummaryDto> list = benchmarkService.getAllBatches();
        return ResponseEntity.ok(list);
    }

    /**
     * Retorna o lote completo mais recente ou executa um automaticamente se nenhum existir.
     */
    @GetMapping("/batches/latest")
    public ResponseEntity<BenchmarkBatchSummaryDto> getLatestBatch() {
        BenchmarkBatchSummaryDto latest = benchmarkService.getLatestBatch();
        if (latest == null) {
            latest = benchmarkService.runAllScenariosInSequence();
        }
        return ResponseEntity.ok(latest);
    }

    /**
     * Retorna os detalhes de um lote específico por batchId.
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<BenchmarkBatchSummaryDto> getBatchDetails(@PathVariable String batchId) {
        BenchmarkBatchSummaryDto details = benchmarkService.getBatchDetails(batchId);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    /**
     * Retorna a lista com todos os testes e sessões de benchmark executados.
     */
    @GetMapping
    public ResponseEntity<List<BenchmarkSessionSummaryDto>> getAllSessions() {
        List<BenchmarkSessionSummaryDto> list = benchmarkService.getAllSessions();
        return ResponseEntity.ok(list);
    }

    /**
     * Retorna o resultado detalhado de uma sessão específica de benchmark,
     * contendo todas as métricas comparativas dos modelos e coordenadas da rota para o mapa.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<BenchmarkSessionSummaryDto> getSessionDetails(@PathVariable String sessionId) {
        BenchmarkSessionSummaryDto details = benchmarkService.getSessionDetails(sessionId);
        if (details == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(details);
    }

    /**
     * Retorna o teste de benchmark mais recente gravado no banco de dados.
     * Se nenhum teste existir ainda, executa um benchmark padrão automaticamente.
     */
    @GetMapping("/latest")
    public ResponseEntity<BenchmarkSessionSummaryDto> getLatestSession() {
        BenchmarkSessionSummaryDto latest = benchmarkService.getLatestSession();
        if (latest == null) {
            // Executa automaticamente uma sessão inicial de calibração para visualização imediata
            latest = benchmarkService.runBenchmark(new BenchmarkRunRequestDto(
                    "C1: CARGA PESADA / B2B DOCA (N = 12 paradas/dia | Campinas)",
                    "Média de mercado para entregas volumosas urbanas. Comparativo multi-algoritmo.",
                    null,
                    null,
                    12,
                    5.0,
                    true,
                    true
            ));
        }
        return ResponseEntity.ok(latest);
    }

    /**
     * Retorna a lista de endereços carregada a partir do arquivo JSON de referência ou cenário.
     */
    @GetMapping("/addresses")
    public ResponseEntity<List<BenchmarkAddressDto>> getAddresses(
            @RequestParam(required = false) String filePath) {
        List<BenchmarkAddressDto> list = benchmarkService.loadAddresses(filePath, null);
        return ResponseEntity.ok(list);
    }

    /**
     * Retorna o catálogo dos 6 cenários calibrados na documentação da IC (C1 a C6).
     */
    @GetMapping("/scenarios")
    public ResponseEntity<List<java.util.Map<String, Object>>> getScenarios() {
        List<java.util.Map<String, Object>> list = benchmarkService.getAvailableScenarios();
        return ResponseEntity.ok(list);
    }
}
