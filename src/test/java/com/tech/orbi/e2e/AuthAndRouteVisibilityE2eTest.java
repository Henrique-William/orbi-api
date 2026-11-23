package com.tech.orbi.e2e;

import com.tech.orbi.dto.CreateDriverDto;
import com.tech.orbi.dto.LoginRequestDto;
import com.tech.orbi.dto.RegisterUserDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Role;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
import com.tech.orbi.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndRouteVisibilityE2eTest extends AbstractOrbiE2ETest {

    // --- Tier 1: Feature Coverage (F1_BE) ---

    @Test
    void t1_loginDriverRole_returnsValidJwtAndHttpOnlyCookie() throws Exception {
        String tag = uniqueTag();
        String email = "driver.auth." + tag + "@orbi.com";
        seedUser("Driver Auth", email, "1191000" + tag, "Pass123!", Role.Values.DRIVER.name());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDto(email, "Pass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(1440))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("SameSite=Strict");
    }

    @Test
    void t1_loginAdminAndBasicRoles_returnsValidJwtsAndRespectsAdminRouteDelete() throws Exception {
        UserSession basic = seedUserWithLogin("basic.vis", Role.Values.BASIC.name(), "BasicPass1!");
        UserSession admin = seedUserWithLogin("admin.vis", Role.Values.ADMIN.name(), "AdminPass1!");

        Route route = seedRoute(null, basic.user(), RouteStatus.CREATED, 1);
        seedDelivery(route, 1, "Av. Paulista, 1000", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(delete("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.jwt()))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route.getId())).isEmpty();
    }

    @Test
    void t1_validateTokenEndpoint_acceptsValidTokenCookie() throws Exception {
        DriverSession driver = seedDriverWithLogin("validate.ok", "DriverPass1!");

        mockMvc.perform(get("/api/auth/validate")
                        .cookie(new Cookie("token", driver.jwt())))
                .andExpect(status().isOk());
    }

    @Test
    void t1_getRoutesAndGetRouteById_exposesNestedDriverIdStatusAndTotals() throws Exception {
        DriverSession driver = seedDriverWithLogin("route.vis", "DriverPass1!");
        UserSession creator = seedUserWithLogin("creator.vis", Role.Values.BASIC.name(), "BasicPass1!");

        Route route = seedRoute(driver.driver(), creator.user(), RouteStatus.ASSIGNED, 2);
        seedDelivery(route, 1, "Rua Augusta, 100", DeliveryStatus.TO_BE_DELIVERED);
        seedDelivery(route, 2, "Rua Consolação, 200", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(get("/api/route")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(route.getId()))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$[0].totalStops").value(2))
                .andExpect(jsonPath("$[0].driverId.userId").value(driver.driver().getUserId().toString()))
                .andExpect(jsonPath("$[0].driverName").value(driver.driver().getDriverName()));

        mockMvc.perform(get("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(route.getId()))
                .andExpect(jsonPath("$.createdBy").value(creator.user().getId().toString()))
                .andExpect(jsonPath("$.driverId.cnh").value(driver.driver().getCnh()));
    }

    @Test
    void t1_getDeliveriesByRoute_returnsOrderedDeliveriesWithDetailsAndStatus() throws Exception {
        DriverSession driver = seedDriverWithLogin("deliv.vis", "DriverPass1!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 2);
        seedDelivery(route, 2, "Parada B - Segundo", DeliveryStatus.TO_BE_DELIVERED);
        seedDelivery(route, 1, "Parada A - Primeiro", DeliveryStatus.IN_TRANSIT);

        mockMvc.perform(get("/api/deliveries/route/{routeId}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].order").value(1))
                .andExpect(jsonPath("$[0].address").value("Parada A - Primeiro"))
                .andExpect(jsonPath("$[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$[1].order").value(2))
                .andExpect(jsonPath("$[1].address").value("Parada B - Segundo"))
                .andExpect(jsonPath("$[1].status").value("TO_BE_DELIVERED"));
    }

    @Test
    void t1_registerAndCreateDriverProfile_promotesUserToDriverAndAllowsDriverLogin() throws Exception {
        String tag = uniqueTag();
        String email = "promoted." + tag + "@orbi.com";
        String phone = "1197000" + tag;
        String cnh = "CNHPROMO" + tag;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterUserDto("Carlos Motorista", email, "Secret123!", phone))))
                .andExpect(status().isOk());

        User registered = userRepository.findByEmail(email).orElseThrow();
        String basicToken = loginAndGetToken(email, "Secret123!");
        Jwt basicJwt = jwtDecoder.decode(basicToken);
        assertThat(basicJwt.getClaimAsString("scope")).isEqualTo(Role.Values.BASIC.name());

        mockMvc.perform(post("/api/user/{userId}/driver-profile", registered.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDriverDto(cnh))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/user/{userId}/driver-profile", registered.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value(cnh))
                .andExpect(jsonPath("$.user.email").value(email));

        User promotedUser = userRepository.findById(registered.getId()).orElseThrow();
        assertThat(promotedUser.getRoles())
                .extracting(Role::getName)
                .containsExactly(Role.Values.DRIVER.name());

        String driverToken = loginAndGetToken(email, "Secret123!");
        Jwt driverJwt = jwtDecoder.decode(driverToken);
        assertThat(driverJwt.getSubject()).isEqualTo(registered.getId().toString());
        assertThat(driverJwt.getClaimAsString("scope")).contains(Role.Values.DRIVER.name());
        assertThat(driverJwt.getClaimAsString("scope")).doesNotContain(Role.Values.BASIC.name());

        mockMvc.perform(get("/api/auth/validate")
                        .cookie(new Cookie("token", driverToken)))
                .andExpect(status().isOk());

        // Exercise a DRIVER-authorized endpoint on an assigned route (createdBy=null)
        Driver promotedDriver = driverRepository.findById(registered.getId()).orElseThrow();
        Route assignedRoute = seedRoute(promotedDriver, null, RouteStatus.ASSIGNED, 1);
        Delivery delivery = seedDelivery(assignedRoute, 1, "Av. Promovido, 50", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Skip com perfil promovido\"}"))
                .andExpect(status().isNoContent());

        Delivery persisted = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(persisted.getDetails()).isEqualTo("Skip com perfil promovido");
    }

    @Test
    void t1_bootstrapAdminUser_retainsScopeAdminAfterCleanupAndCanExecuteAdminEndpoints() throws Exception {
        String bootstrapAdminToken = loginAndGetToken("admin", "e2e-admin-pass");
        Jwt adminJwt = jwtDecoder.decode(bootstrapAdminToken);
        assertThat(adminJwt.getClaimAsString("scope")).contains(Role.Values.ADMIN.name());

        UserSession basic = seedUserWithLogin("boot.target", Role.Values.BASIC.name(), "BasicPass1!");
        Route route = seedRoute(null, basic.user(), RouteStatus.CREATED, 1);
        Delivery stop = seedDelivery(route, 1, "Rua Bootstrap Admin, 10", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", stop.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bootstrapAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Skip pelo admin bootstrap\"}"))
                .andExpect(status().isNoContent());

        assertThat(deliveryRepository.findById(stop.getId()).orElseThrow().getStatus())
                .isEqualTo(DeliveryStatus.SKIPPED);

        mockMvc.perform(delete("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bootstrapAdminToken))
                .andExpect(status().isNoContent());

        assertThat(routeRepository.findById(route.getId())).isEmpty();
    }

    // --- Tier 2: Boundary & Corner Cases (F1_BE) ---

    @Test
    void t2_validateToken_missingOrInvalidOrMalformedCookie_returns401() throws Exception {
        mockMvc.perform(get("/api/auth/validate"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/validate")
                        .cookie(new Cookie("other", "abc")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/validate")
                        .cookie(new Cookie("token", "not.a.valid.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void t2_loginInvalidCredentials_failsAuthenticationAndDoesNotIssueToken() throws Exception {
        DriverSession driver = seedDriverWithLogin("bad.creds", "CorrectPass1!");

        int statusCode;
        try {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequestDto(driver.email(), "WrongPass!"))))
                    .andReturn();
            statusCode = result.getResponse().getStatus();
            assertThat(statusCode).isIn(401, 403);
        } catch (Exception ex) {
            // AuthController throws BadCredentialsException without @ControllerAdvice; ensure it failed authentication
            assertThat(ex).hasStackTraceContaining("BadCredentialsException");
        }
    }

    @Test
    void t2_protectedDeliveryMutationEndpointsWithoutAuthHeader_return401Unauthorized() throws Exception {
        DriverSession driver = seedDriverWithLogin("unauth.check", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 1);
        var delivery = seedDelivery(route, 1, "Rua Sem Auth", DeliveryStatus.TO_BE_DELIVERED);

        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\":\"Teste\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/deliveries/{id}/delivered", delivery.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/deliveries/{id}/next-status", delivery.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void t2_protectedEndpointsWithMalformedOrTamperedBearerToken_return401Unauthorized() throws Exception {
        DriverSession driver = seedDriverWithLogin("tampered.jwt", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 1);
        var delivery = seedDelivery(route, 1, "Rua Token Invalido", DeliveryStatus.TO_BE_DELIVERED);

        String tamperedJwt = driver.jwt().substring(0, driver.jwt().length() - 6) + "AAAAAA";

        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void t2_getRouteAndDeliveriesForNonExistentRouteId_returns404() throws Exception {
        DriverSession driver = seedDriverWithLogin("missing.route", "Pass123!");

        mockMvc.perform(get("/api/route/{id}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/deliveries/route/{routeId}", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void t2_deleteRouteAsDriverRole_returns403Forbidden() throws Exception {
        DriverSession driver = seedDriverWithLogin("del.forbidden", "Pass123!");
        Route route = seedRoute(driver.driver(), driver.user(), RouteStatus.ASSIGNED, 1);

        mockMvc.perform(delete("/api/route/{id}", route.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driver.jwt()))
                .andExpect(status().isForbidden());

        assertThat(routeRepository.findById(route.getId())).isPresent();
    }
}
