package com.tech.orbi.entity;

/**
 * Enum que representa os possíveis estados de uma Rota no sistema.
 * Ciclo de vida: CREATED → ASSIGNED → IN_PROGRESS → COMPLETED
 *                              ↘ CANCELLED ↙
 */
public enum RouteStatus {
    CREATED,       // Rota criada e otimizada
    ASSIGNED,      // Motorista atribuído à rota
    IN_PROGRESS,   // Motorista em execução da rota
    COMPLETED,     // Todas as entregas da rota foram concluídas
    CANCELLED      // Rota cancelada
}
