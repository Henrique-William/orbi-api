-- ==============================================================================
-- Orbi API - Database Migration Patch
-- Atualização dos Ciclos de Vida, Novos Campos e Automação de Rotas/Entregas
-- ==============================================================================

-- 1. ESTRUTURA (DDL)
-- Adiciona novas colunas de rastreabilidade temporal e métricas na tabela tb_routes
-- (Sintaxe compatível com todas as versões do MySQL)
ALTER TABLE tb_routes ADD COLUMN assigned_at DATETIME(6) NULL;
ALTER TABLE tb_routes ADD COLUMN finalized_at DATETIME(6) NULL;
ALTER TABLE tb_routes ADD COLUMN total_stops INT NOT NULL DEFAULT 0;
ALTER TABLE tb_routes ADD COLUMN created_by BINARY(16) NULL;

-- Garante unicidade da CNH dos motoristas
ALTER TABLE tb_driver_profiles ADD CONSTRAINT uk_driver_cnh UNIQUE (driver_cnh);

-- Adiciona coluna de details para registro de motivo de skip/cancel em tb_deliveries
ALTER TABLE tb_deliveries ADD COLUMN details TEXT NULL;

-- 2. MIGRAÇÃO DE DADOS (DML)

-- 2.0 Rotas: Preencher created_by a partir de user_id legado (se houver)
UPDATE tb_routes 
SET created_by = user_id 
WHERE user_id IS NOT NULL AND created_by IS NULL;

-- 2.1 Rotas: Migrar status legado PENDING para CREATED
UPDATE tb_routes 
SET status = 'CREATED' 
WHERE status = 'PENDING';

-- 2.2 Rotas: Preencher retroativamente a quantidade total de paradas (total_stops)
UPDATE tb_routes r
JOIN (
    SELECT route_id, COUNT(*) AS stop_count
    FROM tb_deliveries
    GROUP BY route_id
) d ON r.id = d.route_id
SET r.total_stops = d.stop_count
WHERE r.total_stops = 0;

-- 2.3 Rotas: Preencher assigned_at para rotas existentes com motorista atribuído
UPDATE tb_routes 
SET assigned_at = created_at 
WHERE assigned_driver_id IS NOT NULL AND assigned_at IS NULL;

-- 2.4 Rotas: Preencher finalized_at para rotas que já constavam como COMPLETED
UPDATE tb_routes 
SET finalized_at = created_at 
WHERE status = 'COMPLETED' AND finalized_at IS NULL;

-- 2.5 Entregas: Migrar status legados (REQUESTED, ACCEPTED, AT_PICKUP) para TO_BE_DELIVERED
UPDATE tb_deliveries 
SET status = 'TO_BE_DELIVERED' 
WHERE status IN ('REQUESTED', 'ACCEPTED', 'AT_PICKUP');

-- 2.6 Rotas: Atualizar automaticamente para COMPLETED rotas ativas cujas entregas já foram todas finalizadas
UPDATE tb_routes r
JOIN (
    SELECT route_id, MAX(delivered_at) AS max_delivered_at
    FROM tb_deliveries
    GROUP BY route_id
    HAVING COUNT(*) > 0
       AND SUM(CASE WHEN status NOT IN ('DELIVERED', 'CANCELLED') THEN 1 ELSE 0 END) = 0
) d ON r.id = d.route_id
SET r.status = 'COMPLETED',
    r.finalized_at = COALESCE(d.max_delivered_at, r.created_at)
WHERE r.status NOT IN ('COMPLETED', 'CANCELLED');

-- 2.7 Entregas: Cancelar entregas atreladas a rotas que já constavam como canceladas
UPDATE tb_deliveries d
JOIN tb_routes r ON d.route_id = r.id
SET d.status = 'CANCELLED'
WHERE r.status = 'CANCELLED' 
  AND d.status NOT IN ('DELIVERED', 'CANCELLED');

-- ==============================================================================
-- 3. TABELA DE BENCHMARK DE ALGORITMOS (IC / Orbi Ecosystem)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS benchmark (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    batch_id VARCHAR(64) NULL,
    scenario_code VARCHAR(20) NULL,
    scenario_name VARCHAR(255) NOT NULL,
    scenario_description TEXT NULL,
    stops_count INT NOT NULL,
    algorithm_name VARCHAR(100) NOT NULL,
    algorithm_type VARCHAR(100) NULL,
    complexity VARCHAR(60) NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(30) NOT NULL DEFAULT 'VALID',
    invalid_reason TEXT NULL,
    execution_time_ms DOUBLE NULL,
    total_distance_km DOUBLE NULL,
    total_duration_min DOUBLE NULL,
    total_cost DOUBLE NULL,
    avg_surface_factor DOUBLE NULL,
    efficiency_gain_pct DOUBLE NULL,
    optimality_gap_pct DOUBLE NULL,
    initial_duration_min DOUBLE NULL,
    initial_distance_km DOUBLE NULL,
    tour_order_json TEXT NULL,
    route_coordinates_json LONGTEXT NULL,
    notes TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_benchmark_session (session_id),
    INDEX idx_benchmark_batch (batch_id),
    INDEX idx_benchmark_scenario_code (scenario_code),
    INDEX idx_benchmark_scenario (scenario_name),
    INDEX idx_benchmark_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Garante colunas em bancos existentes
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS batch_id VARCHAR(64) NULL;
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS scenario_code VARCHAR(20) NULL;
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS optimality_gap_pct DOUBLE NULL;
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS is_valid BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'VALID';
ALTER TABLE benchmark ADD COLUMN IF NOT EXISTS invalid_reason TEXT NULL;
ALTER TABLE benchmark MODIFY COLUMN execution_time_ms DOUBLE NULL;
ALTER TABLE benchmark MODIFY COLUMN total_distance_km DOUBLE NULL;
ALTER TABLE benchmark MODIFY COLUMN total_duration_min DOUBLE NULL;
ALTER TABLE benchmark MODIFY COLUMN total_cost DOUBLE NULL;
ALTER TABLE benchmark MODIFY COLUMN avg_surface_factor DOUBLE NULL;

