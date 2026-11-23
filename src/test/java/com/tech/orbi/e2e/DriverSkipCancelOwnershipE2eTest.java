package com.tech.orbi.e2e;

import com.tech.orbi.dto.DeliveryDetailsDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DriverSkipCancelOwnershipE2eTest extends AbstractOrbiE2ETest {

    // --- Tier 1: Feature Coverage (F2_BE) ---

    @Test
    void t1_assignedDriverSkipDelivery_returns204PersistsSkippedAndTransitionsRouteToInProgress() throws Exception {
        DriverSession driver = seedDriverWithLogin("skip.ok", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Rua Alpha, 10", DeliveryStatus.TO_BE_DELIVERED);
        seedDelivery(route, 2, "Rua Beta, 20", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("  Cliente ausente  "))))
                .andExpect(status().isNoContent());

        Delivery updated = deliveryRepository.findById(stop1.getId()).orElseThrow();
        Route updatedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(updated.getDetails()).isEqualTo("Cliente ausente");
        assertThat(updatedRoute.getStatus()).isEqualTo(RouteStatus.IN_PROGRESS);
        assertThat(updatedRoute.getFinalizedAt()).isNull();
    }

    @Test
    void t1_assignedDriverCancelDelivery_returns204PersistsCancelledAndTransitionsRouteToInProgress() throws Exception {
        DriverSession driver = seedDriverWithLogin("cancel.ok", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Rua Gamma, 30", DeliveryStatus.TO_BE_DELIVERED);
        seedDelivery(route, 2, "Rua Delta, 40", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto("  Endereço inacessível  "))))
                .andExpect(status().isNoContent());

        Delivery updated = deliveryRepository.findById(stop1.getId()).orElseThrow();
        Route updatedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(updated.getDetails()).isEqualTo("Endereço inacessível");
        assertThat(updatedRoute.getStatus()).isEqualTo(RouteStatus.IN_PROGRESS);
        assertThat(updatedRoute.getFinalizedAt()).isNull();
    }

    @Test
    void t1_routeCreatorSkipAndCancelDelivery_returns204() throws Exception {
        UserSession creator = seedUserWithLogin("creator.own", Role.Values.BASIC.name(), "BasicPass1!");
        DriverSession assignedDriver = seedDriverWithLogin("assigned.other", "Pass123!");
        Route route = seedRoute(assignedDriver.driver(), creator.user(), RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Av. Criador, 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop2 = seedDelivery(route, 2, "Av. Criador, 2", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Pulada pelo criador\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + creator.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Cancelada pelo criador\"}"))
                .andExpect(status().isNoContent());

        assertThat(deliveryRepository.findById(stop1.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(deliveryRepository.findById(stop2.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
    }

    @Test
    void t1_adminUserSkipAndCancelAnyDelivery_returns204() throws Exception {
        DriverSession driver = seedDriverWithLogin("admin.target", "Pass123!");
        UserSession admin = seedUserWithLogin("admin.skip", Role.Values.ADMIN.name(), "AdminPass1!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        Delivery stop1 = seedDelivery(route, 1, "Rua Admin 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery stop2 = seedDelivery(route, 2, "Rua Admin 2", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Admin skip\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Admin cancel\"}"))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
    }

    @Test
    void t1_assignedDriverDeliverStop_returns204SetsDeliveredAtAndAutoCompletesFinalStop() throws Exception {
        DriverSession driver = seedDriverWithLogin("deliver.done", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 1);
        Delivery stop1 = seedDelivery(route, 1, "Rua Final, 99", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/delivered", stop1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());

        Delivery updated = deliveryRepository.findById(stop1.getId()).orElseThrow();
        Route updatedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(updated.getDeliveredAt()).isNotNull();
        assertThat(updatedRoute.getStatus()).isEqualTo(RouteStatus.COMPLETED);
        assertThat(updatedRoute.getFinalizedAt()).isNotNull();
    }

    @Test
    void t1_assignedDriverNextStatusProgression_transitionsToBeDeliveredToDelivered() throws Exception {
        DriverSession driver = seedDriverWithLogin("next.step", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 1);
        Delivery stop = seedDelivery(route, 1, "Rua Passo a Passo", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(stop.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(deliveryRepository.findById(stop.getId()).orElseThrow().getPickedUpAt()).isNotNull();

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(stop.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(stop.getId()).orElseThrow().getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(routeRepository.findById(route.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.COMPLETED);
    }

    // --- Tier 2: Boundary & Corner Cases (F2_BE) ---

    @Test
    void t2_unassignedDriverSkipCancelAndDelivered_return403AndNeverMutateState() throws Exception {
        DriverSession ownerDriver = seedDriverWithLogin("owner.a", "Pass123!");
        DriverSession intruderDriver = seedDriverWithLogin("intruder.b", "Pass123!");

        Route route = seedRoute(ownerDriver.driver(), ownerDriver.user(), RouteStatus.ASSIGNED, 1);
        Delivery stop = seedDelivery(route, 1, "Rua Protegida, 123", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderDriver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Tentativa indevida\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderDriver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Tentativa indevida\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/delivered", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderDriver.jwt()))
                .andExpect(status().isForbidden());

        Delivery persisted = deliveryRepository.findById(stop.getId()).orElseThrow();
        Route persistedRoute = routeRepository.findById(route.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(persisted.getDetails()).isNull();
        assertThat(persistedRoute.getStatus()).isEqualTo(RouteStatus.ASSIGNED);
    }

    @Test
    void t2_routeWithNullDriverWhenUnassignedDriverAttemptsSkipOrCancel_returns403AndDoesNotAutoAssign() throws Exception {
        UserSession creator = seedUserWithLogin("creator.nodrv", Role.Values.BASIC.name(), "BasicPass1!");
        DriverSession randomDriver = seedDriverWithLogin("random.drv", "Pass123!");

        Route driverlessRoute = seedRoute(null, creator.user(), RouteStatus.CREATED, 1);
        Delivery stop = seedDelivery(driverlessRoute, 1, "Rua Sem Motorista", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + randomDriver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Sem dono\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + randomDriver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Sem dono\"}"))
                .andExpect(status().isForbidden());

        Route persistedRoute = routeRepository.findById(driverlessRoute.getId()).orElseThrow();
        Delivery persistedDelivery = deliveryRepository.findById(stop.getId()).orElseThrow();
        assertThat(persistedRoute.getDriver()).isNull();
        assertThat(persistedRoute.getStatus()).isEqualTo(RouteStatus.CREATED);
        assertThat(persistedDelivery.getStatus()).isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
    }

    @Test
    void t2_nonExistentDeliveryIdOnSkipCancelDelivered_returns404() throws Exception {
        DriverSession driver = seedDriverWithLogin("notfound.drv", "Pass123!");

        mockMvc.perform(patch("/api/deliveries/{id}/skip", 888888L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Inexistente\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", 888888L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Inexistente\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/deliveries/{id}/delivered", 888888L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void t2_justificationVariants_omittedBodyEmptyBlankLongAndUnicodePortuguese_persistAccurately() throws Exception {
        DriverSession driver = seedDriverWithLogin("justif.bva", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 4);

        Delivery dNoBody = seedDelivery(route, 1, "Stop 1", DeliveryStatus.TO_BE_DELIVERED);
        Delivery dBlank = seedDelivery(route, 2, "Stop 2", DeliveryStatus.TO_BE_DELIVERED);
        Delivery dLong = seedDelivery(route, 3, "Stop 3", DeliveryStatus.TO_BE_DELIVERED);
        Delivery dUnicode = seedDelivery(route, 4, "Stop 4", DeliveryStatus.TO_BE_DELIVERED);

        // Omitted request body
        mockMvc.perform(patch("/api/deliveries/{id}/skip", dNoBody.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(dNoBody.getId()).orElseThrow().getDetails()).isNull();

        // Whitespace-only body should not persist blank text
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", dBlank.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"   \\t\\n  \"}"))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(dBlank.getId()).orElseThrow().getDetails()).isNull();

        // Long 500-character string
        String longReason = "Motivo longo: " + "A".repeat(486);
        mockMvc.perform(patch("/api/deliveries/{id}/skip", dLong.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto(longReason))))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(dLong.getId()).orElseThrow().getDetails()).isEqualTo(longReason);

        // Portuguese Unicode + special characters with surrounding whitespace
        String unicodeReason = "  Portão fechado nº 42 — atenção: cão bravo (100% verificado!)  ";
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", dUnicode.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeliveryDetailsDto(unicodeReason))))
                .andExpect(status().isNoContent());
        assertThat(deliveryRepository.findById(dUnicode.getId()).orElseThrow().getDetails())
                .isEqualTo(unicodeReason.trim());
    }

    @Test
    void t2_terminalDeliveries_cannotBeReSkippedReCancelledDeliveredOrNextStatus_returns400AndLeavesDbUnchanged() throws Exception {
        DriverSession driver = seedDriverWithLogin("terminal.bva", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.IN_PROGRESS, 3);

        Delivery dDelivered = seedDelivery(route, 1, "Delivered Stop", DeliveryStatus.DELIVERED);
        dDelivered.setDetails("Entregue OK");
        dDelivered = deliveryRepository.saveAndFlush(dDelivered);

        Delivery dCancelled = seedDelivery(route, 2, "Cancelled Stop", DeliveryStatus.CANCELLED);
        dCancelled.setDetails("Cancelado original");
        dCancelled = deliveryRepository.saveAndFlush(dCancelled);

        Delivery dSkipped = seedDelivery(route, 3, "Skipped Stop", DeliveryStatus.SKIPPED);
        dSkipped.setDetails("Pulado original");
        dSkipped = deliveryRepository.saveAndFlush(dSkipped);

        for (Delivery terminal : new Delivery[]{dDelivered, dCancelled, dSkipped}) {
            DeliveryStatus expectedStatus = terminal.getStatus();
            String expectedDetails = terminal.getDetails();

            mockMvc.perform(patch("/api/deliveries/{id}/skip", terminal.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"details\":\"Retry skip\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/deliveries/{id}/cancel", terminal.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"details\":\"Retry cancel\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/deliveries/{id}/delivered", terminal.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/deliveries/{id}/next-status", terminal.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                    .andExpect(status().isBadRequest());

            Delivery persisted = deliveryRepository.findById(terminal.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(expectedStatus);
            assertThat(persisted.getDetails()).isEqualTo(expectedDetails);
        }
    }

    @Test
    void t2_basicNonCreatorWithoutDriverProfile_skipCancelDeliveredOnAssignedAndDriverlessRoutes_returns403AndNeverMutatesState() throws Exception {
        DriverSession ownerDriver = seedDriverWithLogin("basic.owner", "Pass123!");
        UserSession creator = seedUserWithLogin("basic.creator", Role.Values.BASIC.name(), "BasicPass1!");
        UserSession unrelatedBasic = seedUserWithLogin("basic.intruder", Role.Values.BASIC.name(), "BasicPass1!");

        Route assignedRoute = seedRoute(ownerDriver.driver(), creator.user(), RouteStatus.ASSIGNED, 1);
        Delivery assignedStop = seedDelivery(assignedRoute, 1, "Rua Atribuida, 10", DeliveryStatus.TO_BE_DELIVERED);

        Route driverlessRoute = seedRoute(null, creator.user(), RouteStatus.CREATED, 1);
        Delivery driverlessStop = seedDelivery(driverlessRoute, 1, "Rua Sem Motorista, 20", DeliveryStatus.TO_BE_DELIVERED);

        for (Delivery target : new Delivery[]{assignedStop, driverlessStop}) {
            mockMvc.perform(patch("/api/deliveries/{id}/skip", target.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedBasic.jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"details\":\"BASIC nao criador\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/deliveries/{id}/cancel", target.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedBasic.jwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"details\":\"BASIC nao criador\"}"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/deliveries/{id}/delivered", target.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedBasic.jwt()))
                    .andExpect(status().isForbidden());
        }

        Delivery persistedAssigned = deliveryRepository.findById(assignedStop.getId()).orElseThrow();
        assertThat(persistedAssigned.getStatus()).isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(persistedAssigned.getDetails()).isNull();
        assertThat(routeRepository.findById(assignedRoute.getId()).orElseThrow().getStatus()).isEqualTo(RouteStatus.ASSIGNED);

        Delivery persistedDriverless = deliveryRepository.findById(driverlessStop.getId()).orElseThrow();
        Route persistedDriverlessRoute = routeRepository.findById(driverlessRoute.getId()).orElseThrow();
        assertThat(persistedDriverless.getStatus()).isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(persistedDriverless.getDetails()).isNull();
        assertThat(persistedDriverlessRoute.getStatus()).isEqualTo(RouteStatus.CREATED);
        assertThat(persistedDriverlessRoute.getDriver()).isNull();
    }

    @Test
    void t2_jsonBodyVariants_emptyObjectNullDetailsAndMalformedJson_return204And400() throws Exception {
        DriverSession driver = seedDriverWithLogin("json.variants", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 3);

        Delivery dEmptyObj = seedDelivery(route, 1, "Empty JSON Object", DeliveryStatus.TO_BE_DELIVERED);
        Delivery dNullDetails = seedDelivery(route, 2, "Null Details", DeliveryStatus.TO_BE_DELIVERED);
        Delivery dMalformed = seedDelivery(route, 3, "Malformed JSON", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", dEmptyObj.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
        Delivery persistedEmpty = deliveryRepository.findById(dEmptyObj.getId()).orElseThrow();
        assertThat(persistedEmpty.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(persistedEmpty.getDetails()).isNull();

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", dNullDetails.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": null}"))
                .andExpect(status().isNoContent());
        Delivery persistedNull = deliveryRepository.findById(dNullDetails.getId()).orElseThrow();
        assertThat(persistedNull.getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        assertThat(persistedNull.getDetails()).isNull();

        mockMvc.perform(patch("/api/deliveries/{id}/skip", dMalformed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", dMalformed.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest());

        Delivery persistedMalformed = deliveryRepository.findById(dMalformed.getId()).orElseThrow();
        assertThat(persistedMalformed.getStatus()).isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(persistedMalformed.getDetails()).isNull();
    }

    @Test
    void t2_nextStatusUnauthorizedAndMissingId_returns403And404AndNeverMutatesState() throws Exception {
        DriverSession ownerDriver = seedDriverWithLogin("next.owner", "Pass123!");
        DriverSession intruderDriver = seedDriverWithLogin("next.intruder", "Pass123!");
        UserSession unrelatedBasic = seedUserWithLogin("next.basic", Role.Values.BASIC.name(), "BasicPass1!");

        Route assignedRoute = seedRoute(ownerDriver.driver(), ownerDriver.user(), RouteStatus.ASSIGNED, 1);
        Delivery assignedStop = seedDelivery(assignedRoute, 1, "Rua Next Assigned", DeliveryStatus.TO_BE_DELIVERED);

        Route driverlessRoute = seedRoute(null, ownerDriver.user(), RouteStatus.CREATED, 1);
        Delivery driverlessStop = seedDelivery(driverlessRoute, 1, "Rua Next Driverless", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", assignedStop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderDriver.jwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", assignedStop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedBasic.jwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", driverlessStop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderDriver.jwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", 888888L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerDriver.jwt()))
                .andExpect(status().isNotFound());

        assertThat(deliveryRepository.findById(assignedStop.getId()).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(deliveryRepository.findById(driverlessStop.getId()).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.TO_BE_DELIVERED);
        assertThat(routeRepository.findById(driverlessRoute.getId()).orElseThrow().getDriver()).isNull();
    }

    @Test
    void t2_corsPreflightOptionsForSkipAndCancel_returns200AndAllowsPatch() throws Exception {
        mockMvc.perform(options("/api/deliveries/1/skip")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8081"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));

        mockMvc.perform(options("/api/deliveries/1/cancel")
                        .header(HttpHeaders.ORIGIN, "http://127.0.0.1:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:8081"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }

    @Test
    void t2_deliverStopOnRouteWithoutDriverEvenForAdmin_returns400BadRequest() throws Exception {
        UserSession admin = seedUserWithLogin("admin.nodrv", Role.Values.ADMIN.name(), "AdminPass1!");
        Route driverlessRoute = seedRoute(null, null, RouteStatus.CREATED, 1);
        Delivery stop = seedDelivery(driverlessRoute, 1, "Sem Motorista", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/delivered", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt()))
                .andExpect(status().isBadRequest());
    }
}
