package com.tech.orbi.e2e;

import com.tech.orbi.dto.DeliveryDetailsDto;
import com.tech.orbi.dto.RouteAssignDriverDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackendCrossFeaturePairwiseE2eTest extends AbstractOrbiE2ETest {

    // --- Tier 3: Cross-Feature Backend Pairwise Combinations ---

    @Test
    void t3_multiStopMixedDeliverSkipCancelSequence_transitionsAssignedToInProgressToCompletedWithFinalizedAt() throws Exception {
        DriverSession driver = seedDriverWithLogin("pairwise.mix", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 3);

        Delivery stop1 = seedDelivery(route, 1, "Parada 1 - Entregar", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop2 = seedDelivery(route, 2, "Parada 2 - Pular", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop3 = seedDelivery(route, 3, "Parada 3 - Cancelar", DeliveryStatus.TO_BE_DELIVERED);

        // Stop 1: DELIVERED -> Route becomes IN_PROGRESS
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.finalizedAt").doesNotExist());

        // Stop 2: SKIPPED -> Route remains IN_PROGRESS
        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("Destinatário em reunião"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Stop 3: CANCELLED -> All terminal, Route auto-completes with finalizedAt
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop3.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("Pedido recusado"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalizedAt").isNotEmpty());

        mockMvc.perform(get("/api/deliveries/route/{routeId}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$[1].details").value("Destinatário em reunião"))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$[2].details").value("Pedido recusado"));
    }

    @Test
    void t3_driverReassignmentViaAssignDriver_revokesOldDriverAccess403AndGrantsNewDriver204() throws Exception {
        UserSession admin = seedUserWithLogin("admin.reassign", Role.Values.ADMIN.name(), "AdminPass1!");
        DriverSession driverA = seedDriverWithLogin("reassign.a", "Pass123!");
        DriverSession driverB = seedDriverWithLogin("reassign.b", "Pass123!");

        Route route = seedRoute(driverA.driver(), null, RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Parada A", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop2 = seedDelivery(route, 2, "Parada B", DeliveryStatus.TO_BE_DELIVERED);

        // Driver A skips Stop 1 while assigned
        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverA.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Turno encerrado\"}"))
                .andExpect(status().isNoContent());

        // Admin reassigns route to Driver B
        mockMvc.perform(patch("/api/route/{id}/assign-driver", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RouteAssignDriverDto(driverB.driver().getUserId().toString()))))
                .andExpect(status().isNoContent());

        // Old Driver A can no longer mutate Stop 2 -> 403
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverA.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Antigo motorista\"}"))
                .andExpect(status().isForbidden());

        // New Driver B can deliver Stop 2 -> 204 and auto-completes route
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", stop2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverB.jwt()))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
    }

    @Test
    void t3_corsPreflightLoginPatchSkipCancelAndGetDeliveriesSyncRoundtrip() throws Exception {
        DriverSession driver = seedDriverWithLogin("cors.roundtrip", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Rua CORS 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop2 = seedDelivery(route, 2, "Rua CORS 2", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(options("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));

        String freshToken = loginAndGetToken(driver.email(), "Pass123!");

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Sem acesso à portaria\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop2.getId())
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Cliente cancelou pedido\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/deliveries/route/{routeId}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SKIPPED"))
                .andExpect(jsonPath("$[0].details").value("Sem acesso à portaria"))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].details").value("Cliente cancelou pedido"));
    }

    @Test
    void t3_creatorVsAssignedDriverVsUnrelatedDriverVsAdminCrossRoleHandoff() throws Exception {
        UserSession creator = seedUserWithLogin("handoff.creator", Role.Values.BASIC.name(), "BasicPass1!");
        DriverSession assigned = seedDriverWithLogin("handoff.assigned", "Pass123!");
        DriverSession unrelated = seedDriverWithLogin("handoff.unrelated", "Pass123!");
        UserSession admin = seedUserWithLogin("admin.handoff", Role.Values.ADMIN.name(), "AdminPass1!");

        Route route = seedRoute(assigned.driver(), creator.user(), RouteStatus.ASSIGNED, 3);
        Delivery s1 = seedDelivery(route, 1, "Handoff 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery s2 = seedDelivery(route, 2, "Handoff 2", DeliveryStatus.TO_BE_DELIVERED);
        Delivery s3 = seedDelivery(route, 3, "Handoff 3", DeliveryStatus.TO_BE_DELIVERED);

        // Unrelated driver is rejected on all stops
        mockMvc.perform(patch("/api/deliveries/{id}/skip", s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelated.jwt()))
                .andExpect(status().isForbidden());

        // Creator skips stop 1
        mockMvc.perform(patch("/api/deliveries/{id}/skip", s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Reagendado pelo dispatcher\"}"))
                .andExpect(status().isNoContent());

        // Assigned driver delivers stop 2
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", s2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + assigned.jwt()))
                .andExpect(status().isNoContent());

        // Admin cancels stop 3 -> completes route
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", s3.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Cancelamento administrativo\"}"))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
    }
}
