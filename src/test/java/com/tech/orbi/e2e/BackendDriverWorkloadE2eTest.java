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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BackendDriverWorkloadE2eTest extends AbstractOrbiE2ETest {

    // --- Tier 4: Real-World Backend Driver Shift Workloads ---

    @Test
    void t4_fullDriverShiftAcrossTwoRoutes_mixedDeliverSkipCancelAutoCompletesBothRoutes() throws Exception {
        DriverSession driver = seedDriverWithLogin("shift.multi", "ShiftPass1!");

        // Route 1: 2 stops, both delivered
        Route route1 = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        Delivery r1s1 = seedDelivery(route1, 1, "Zona Sul - Stop 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery r1s2 = seedDelivery(route1, 2, "Zona Sul - Stop 2", DeliveryStatus.TO_BE_DELIVERED);

        // Route 2: 3 stops (deliver + customer-absent skip + damaged-package cancel)
        Route route2 = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 3);
        Delivery r2s1 = seedDelivery(route2, 1, "Centro - Stop 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery r2s2 = seedDelivery(route2, 2, "Centro - Stop 2", DeliveryStatus.TO_BE_DELIVERED);
        Delivery r2s3 = seedDelivery(route2, 3, "Centro - Stop 3", DeliveryStatus.TO_BE_DELIVERED);

        // Driver lists routes at start of shift
        mockMvc.perform(get("/api/route")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Execute Route 1
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", r1s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", r1s2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route1.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);

        // Execute Route 2 mixed outcomes
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", r2s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/skip", r2s2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("Cliente ausente"))))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route2.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.IN_PROGRESS);

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", r2s3.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("Mercadoria avariada"))))
                .andExpect(status().isNoContent());

        Route completedRoute2 = routeRepository.findById(route2.getId()).orElseThrow();
        assertThat(completedRoute2.getStatus()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(completedRoute2.getFinalizedAt()).isNotNull();
    }

    @Test
    void t4_multiDriverIsolatedConcurrentShift_driverAAndDriverBStrictlyIsolated() throws Exception {
        DriverSession driverA = seedDriverWithLogin("shift.a", "PassA123!");
        DriverSession driverB = seedDriverWithLogin("shift.b", "PassB123!");

        Route routeA = seedRoute(driverA.driver(), null, RouteStatus.ASSIGNED, 2);
        Delivery a1 = seedDelivery(routeA, 1, "Rota A - 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery a2 = seedDelivery(routeA, 2, "Rota A - 2", DeliveryStatus.TO_BE_DELIVERED);

        Route routeB = seedRoute(driverB.driver(), null, RouteStatus.ASSIGNED, 2);
        Delivery b1 = seedDelivery(routeB, 1, "Rota B - 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery b2 = seedDelivery(routeB, 2, "Rota B - 2", DeliveryStatus.TO_BE_DELIVERED);

        // Cross-driver interference blocked with 403
        mockMvc.perform(patch("/api/deliveries/{id}/skip", b1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverA.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Invasão A\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", a1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverB.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Invasão B\"}"))
                .andExpect(status().isForbidden());

        // Each driver completes their own route
        mockMvc.perform(patch("/api/deliveries/{id}/skip", a1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverA.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Motivo A1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", a2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverA.jwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", b1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverB.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Motivo B1\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", b2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverB.jwt()))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(routeA.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(routeRepository.findById(routeB.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(deliveryRepository.findById(a1.getId()).orElseThrow().getDetails()).isEqualTo("Motivo A1");
        assertThat(deliveryRepository.findById(b1.getId()).orElseThrow().getDetails()).isEqualTo("Motivo B1");
    }

    @Test
    void t4_dispatchToCompletionLifecycle_dispatcherCreatesAssignsDriverExecutesAndVerifiesSummary() throws Exception {
        UserSession dispatcher = seedUserWithLogin("dispatcher", Role.Values.BASIC.name(), "DispPass1!");
        DriverSession driver = seedDriverWithLogin("courier", "CourPass1!");

        // Dispatcher seeds CREATED route with no driver yet
        Route route = seedRoute(null, dispatcher.user(), RouteStatus.CREATED, 3);
        Delivery s1 = seedDelivery(route, 1, "Dispatch Stop 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery s2 = seedDelivery(route, 2, "Dispatch Stop 2", DeliveryStatus.TO_BE_DELIVERED);
        Delivery s3 = seedDelivery(route, 3, "Dispatch Stop 3", DeliveryStatus.TO_BE_DELIVERED);

        // Before assignment, driver cannot touch stops -> 403
        mockMvc.perform(patch("/api/deliveries/{id}/skip", s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isForbidden());

        // Dispatcher assigns driver via API -> CREATED transitions to ASSIGNED
        mockMvc.perform(patch("/api/route/{id}/assign-driver", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcher.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RouteAssignDriverDto(driver.driver().getUserId().toString()))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driverId.userId").value(driver.driver().getUserId().toString()));

        // Driver executes full lifecycle
        mockMvc.perform(patch("/api/deliveries/{id}/next-status", s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/delivered", s1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/skip", s2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Local fechado no horário\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", s3.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Cliente desistiu\"}"))
                .andExpect(status().isNoContent());

        // Verify finalized route summary and deliveries audit trail
        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcher.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finalizedAt").isNotEmpty());

        mockMvc.perform(get("/api/deliveries/route/{routeId}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcher.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"));
    }
}
