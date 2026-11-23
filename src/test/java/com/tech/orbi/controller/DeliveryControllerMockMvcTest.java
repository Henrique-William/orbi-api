package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    private UUID assignedDriverId;
    private UUID otherDriverId;
    private Route route;
    private Delivery delivery1;
    private Delivery delivery2;

    @BeforeEach
    void setUp() {
        deliveryRepository.deleteAll();
        routeRepository.deleteAll();
        driverRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> !"admin".equals(u.getEmail()))
                .forEach(userRepository::delete);

        User assignedUser = new User();
        assignedUser.setName("Driver Assigned");
        assignedUser.setEmail("driver.assigned@orbi.test");
        assignedUser.setPhone("11999990001");
        assignedUser.setPassword("encoded-pass");
        assignedUser = userRepository.save(assignedUser);
        assignedDriverId = assignedUser.getId();

        Driver assignedDriver = new Driver();
        assignedDriver.setUserId(assignedDriverId);
        assignedDriver.setDriverName("Driver Assigned");
        assignedDriver.setCnh("CNH-0001-" + assignedDriverId.toString().substring(0, 6));
        assignedDriver.setPhone("11999990001");
        assignedDriver = driverRepository.save(assignedDriver);

        User otherUser = new User();
        otherUser.setName("Driver Other");
        otherUser.setEmail("driver.other@orbi.test");
        otherUser.setPhone("11999990002");
        otherUser.setPassword("encoded-pass");
        otherUser = userRepository.save(otherUser);
        otherDriverId = otherUser.getId();

        Driver otherDriver = new Driver();
        otherDriver.setUserId(otherDriverId);
        otherDriver.setDriverName("Driver Other");
        otherDriver.setCnh("CNH-0002-" + otherDriverId.toString().substring(0, 6));
        otherDriver.setPhone("11999990002");
        driverRepository.save(otherDriver);

        route = new Route();
        route.setDriver(assignedDriver);
        route.setStatus(RouteStatus.ASSIGNED);
        route.setTotalDistance(12.5);
        route.setTotalCost(25.0);
        route.setTotalStops(2);
        route = routeRepository.save(route);

        delivery1 = new Delivery();
        delivery1.setOrder(1);
        delivery1.setAddress("Rua A, 100");
        delivery1.setLatitude(new BigDecimal("-23.55052000"));
        delivery1.setLongitude(new BigDecimal("-46.63330800"));
        delivery1.setStatus(DeliveryStatus.TO_BE_DELIVERED);
        delivery1.setRoute(route);
        delivery1 = deliveryRepository.save(delivery1);

        delivery2 = new Delivery();
        delivery2.setOrder(2);
        delivery2.setAddress("Rua B, 200");
        delivery2.setLatitude(new BigDecimal("-23.55152000"));
        delivery2.setLongitude(new BigDecimal("-46.63430800"));
        delivery2.setStatus(DeliveryStatus.TO_BE_DELIVERED);
        delivery2.setRoute(route);
        delivery2 = deliveryRepository.save(delivery2);
    }

    private String mintDriverJwt(UUID subjectId, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orbi")
                .subject(subjectId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("scope", scope)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    void skipDeliveryByAssignedScopeDriverTransitionsDeliveryAndRouteToInProgress() throws Exception {
        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                        .with(jwt()
                                .jwt(j -> j.subject(assignedDriverId.toString()).claim("scope", "DRIVER"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_DRIVER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"  Cliente ausente  \"}"))
                .andExpect(status().isNoContent());

        Delivery updated = deliveryRepository.findById(delivery1.getId()).orElseThrow();
        Route updatedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertEquals(DeliveryStatus.SKIPPED, updated.getStatus());
        assertEquals("Cliente ausente", updated.getDetails());
        assertEquals(RouteStatus.IN_PROGRESS, updatedRoute.getStatus());
        assertNull(updatedRoute.getFinalizedAt());
    }

    @Test
    void cancelDeliveryByAssignedScopeDriverTransitionsDeliveryAndRouteToInProgress() throws Exception {
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery1.getId())
                        .with(jwt()
                                .jwt(j -> j.subject(assignedDriverId.toString()).claim("scope", "DRIVER"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_DRIVER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"  Endereço incorreto  \"}"))
                .andExpect(status().isNoContent());

        Delivery updated = deliveryRepository.findById(delivery1.getId()).orElseThrow();
        Route updatedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertEquals(DeliveryStatus.CANCELLED, updated.getStatus());
        assertEquals("Endereço incorreto", updated.getDetails());
        assertEquals(RouteStatus.IN_PROGRESS, updatedRoute.getStatus());
        assertNull(updatedRoute.getFinalizedAt());
    }

    @Test
    void skipAndCancelWithRealSignedRs256BearerJwtAutoCompletesRouteWhenAllTerminal() throws Exception {
        String driverToken = mintDriverJwt(assignedDriverId, "DRIVER");

        // 1. Skip first stop using real Bearer JWT -> Route moves ASSIGNED -> IN_PROGRESS
        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"Portão fechado\"}"))
                .andExpect(status().isNoContent());

        Route midRoute = routeRepository.findById(route.getId()).orElseThrow();
        assertEquals(RouteStatus.IN_PROGRESS, midRoute.getStatus());
        assertNull(midRoute.getFinalizedAt());

        // 2. Cancel second (last) stop using real Bearer JWT -> Route auto-completes COMPLETED
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"Cliente desistiu\"}"))
                .andExpect(status().isNoContent());

        Delivery d1 = deliveryRepository.findById(delivery1.getId()).orElseThrow();
        Delivery d2 = deliveryRepository.findById(delivery2.getId()).orElseThrow();
        Route completedRoute = routeRepository.findById(route.getId()).orElseThrow();

        assertEquals(DeliveryStatus.SKIPPED, d1.getStatus());
        assertEquals("Portão fechado", d1.getDetails());
        assertEquals(DeliveryStatus.CANCELLED, d2.getStatus());
        assertEquals("Cliente desistiu", d2.getDetails());
        assertEquals(RouteStatus.COMPLETED, completedRoute.getStatus());
        assertNotNull(completedRoute.getFinalizedAt());
    }

    @Test
    void skipAndCancelSupportOptionalAndAbsentRequestBodies() throws Exception {
        String driverToken = mintDriverJwt(assignedDriverId, "DRIVER");

        // Empty JSON object {} with Content-Type: application/json
        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
        assertNull(deliveryRepository.findById(delivery1.getId()).orElseThrow().getDetails());

        // Empty string body with Content-Type: application/json
        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isNoContent());
        assertNull(deliveryRepository.findById(delivery2.getId()).orElseThrow().getDetails());
    }

    @Test
    void skipAndCancelWithNoBodyAndNoContentTypeHeaderReturn204() throws Exception {
        String driverToken = mintDriverJwt(assignedDriverId, "DRIVER");

        // No Content-Type header and no body at all
        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery2.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isNoContent());

        assertEquals(DeliveryStatus.SKIPPED, deliveryRepository.findById(delivery1.getId()).orElseThrow().getStatus());
        assertEquals(DeliveryStatus.CANCELLED, deliveryRepository.findById(delivery2.getId()).orElseThrow().getStatus());
        assertEquals(RouteStatus.COMPLETED, routeRepository.findById(route.getId()).orElseThrow().getStatus());
    }

    @Test
    void skipAndCancelByUnassignedScopeDriverReturn403AndLeaveDbUnchanged() throws Exception {
        String otherToken = mintDriverJwt(otherDriverId, "DRIVER");

        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"Tentativa indevida\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery1.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": \"Tentativa indevida\"}"))
                .andExpect(status().isForbidden());

        Delivery unchangedDelivery = deliveryRepository.findById(delivery1.getId()).orElseThrow();
        Route unchangedRoute = routeRepository.findById(route.getId()).orElseThrow();
        assertEquals(DeliveryStatus.TO_BE_DELIVERED, unchangedDelivery.getStatus());
        assertNull(unchangedDelivery.getDetails());
        assertEquals(RouteStatus.ASSIGNED, unchangedRoute.getStatus());
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingDeliveryIdReturns404ForScopeDriver() throws Exception {
        String driverToken = mintDriverJwt(assignedDriverId, "DRIVER");

        mockMvc.perform(patch("/api/deliveries/{id}/skip", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/deliveries/{id}/cancel", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void alreadyFinalizedDeliveriesReturn400OnSkipAndCancel() throws Exception {
        String driverToken = mintDriverJwt(assignedDriverId, "DRIVER");

        for (DeliveryStatus terminalStatus : List.of(
                DeliveryStatus.DELIVERED,
                DeliveryStatus.CANCELLED,
                DeliveryStatus.SKIPPED)) {
            delivery1.setStatus(terminalStatus);
            deliveryRepository.save(delivery1);

            mockMvc.perform(patch("/api/deliveries/{id}/skip", delivery1.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/deliveries/{id}/cancel", delivery1.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void corsOptionsPreflightForPatchAllowsPatchMethod() throws Exception {
        mockMvc.perform(options("/api/deliveries/{id}/skip", delivery1.getId())
                        .header(HttpHeaders.ORIGIN, "http://localhost:8081")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8081"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));

        mockMvc.perform(options("/api/deliveries/{id}/cancel", delivery1.getId())
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
    }
}
