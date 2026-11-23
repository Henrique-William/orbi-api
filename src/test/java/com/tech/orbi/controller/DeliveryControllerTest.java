package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.DeliveryDetailsDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
import com.tech.orbi.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private DeliveryController deliveryController;

    private UUID driverId;
    private Driver driver;
    private Route route;
    private Delivery delivery;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        driver = new Driver();
        driver.setUserId(driverId);

        route = new Route();
        route.setId(10L);
        route.setDriver(driver);
        route.setStatus(RouteStatus.IN_PROGRESS);

        delivery = new Delivery();
        delivery.setId(1L);
        delivery.setRoute(route);
        delivery.setStatus(DeliveryStatus.TO_BE_DELIVERED);

        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(driverId.toString());
        lenient().doReturn(jwt).when(authentication).getPrincipal();
        lenient().doReturn(List.of(new SimpleGrantedAuthority("SCOPE_BASIC"))).when(authentication).getAuthorities();
    }

    @Test
    void testNextStatusFromToBeDeliveredToInTransitUpdatesPickedUpAt() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        ResponseEntity<Void> response = deliveryController.nextStatus(1L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
        assertNotNull(delivery.getPickedUpAt());
        assertNull(delivery.getDeliveredAt());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testNextStatusFromToBeDeliveredToInTransitMovesRouteFromAssignedToInProgress() {
        route.setStatus(RouteStatus.ASSIGNED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(delivery));

        ResponseEntity<Void> response = deliveryController.nextStatus(1L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
        assertEquals(RouteStatus.IN_PROGRESS, route.getStatus());
        verify(routeRepository).save(route);
    }

    @Test
    void testNextStatusFromInTransitToOutOfDelivery() {
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        ResponseEntity<Void> response = deliveryController.nextStatus(1L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.OUT_FOR_DELIVERY, delivery.getStatus());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testNextStatusFromOutOfDeliveryToDeliveredCompletesRouteIfAllDone() {
        delivery.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Delivery completedDelivery = new Delivery();
        completedDelivery.setId(1L);
        completedDelivery.setStatus(DeliveryStatus.DELIVERED);

        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(completedDelivery));

        ResponseEntity<Void> response = deliveryController.nextStatus(1L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getDeliveredAt());
        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertNotNull(route.getFinalizedAt());
        verify(routeRepository).save(route);
    }

    @Test
    void testNextStatusWhenAlreadyDeliveredThrowsBadRequest() {
        delivery.setStatus(DeliveryStatus.DELIVERED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.nextStatus(1L, authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testCancelDeliverySuccessAndCompletesRouteIfAllDone() {
        delivery.setStatus(DeliveryStatus.IN_TRANSIT);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Delivery cancelledDelivery = new Delivery();
        cancelledDelivery.setId(1L);
        cancelledDelivery.setStatus(DeliveryStatus.CANCELLED);

        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(cancelledDelivery));

        ResponseEntity<Void> response = deliveryController.cancelDelivery(1L, null, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertNotNull(route.getFinalizedAt());
        verify(routeRepository).save(route);
    }

    @Test
    void testCancelDeliveryWhenAlreadyDeliveredThrowsBadRequest() {
        delivery.setStatus(DeliveryStatus.DELIVERED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.cancelDelivery(1L, null, authentication));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testNextStatusThrowsBadRequestWhenRouteHasNoDriverEvenForAdmin() {
        route.setDriver(null);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication adminAuth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_ADMIN"))).when(adminAuth).getAuthorities();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.nextStatus(1L, adminAuth));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("não possui motorista atribuído"));
    }

    @Test
    void testCancelDeliveryAllowedEvenWhenRouteHasNoDriverForAdmin() {
        route.setDriver(null);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication adminAuth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_ADMIN"))).when(adminAuth).getAuthorities();

        ResponseEntity<Void> response = deliveryController.cancelDelivery(1L, null, adminAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testNextStatusAllowedForAssignedDriverWithScopeDriver() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication driverAuth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(driverId.toString());
        doReturn(jwt).when(driverAuth).getPrincipal();
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_DRIVER"))).when(driverAuth).getAuthorities();

        ResponseEntity<Void> response = deliveryController.nextStatus(1L, driverAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.IN_TRANSIT, delivery.getStatus());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testNextStatusForbiddenForUnassignedDriver() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication otherDriverAuth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        UUID otherDriverId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(otherDriverId.toString());
        doReturn(jwt).when(otherDriverAuth).getPrincipal();
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_DRIVER"))).when(otherDriverAuth).getAuthorities();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.nextStatus(1L, otherDriverAuth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testGetDeliveriesByRouteIdSuccess() {
        when(routeRepository.existsById(10L)).thenReturn(true);
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(delivery));

        ResponseEntity<List<com.tech.orbi.dto.DeliveryDto>> response = deliveryController.getDeliveriesByRouteId(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(1L, response.getBody().get(0).id());
        assertEquals(DeliveryStatus.TO_BE_DELIVERED, response.getBody().get(0).status());
    }

    @Test
    void testGetDeliveriesByRouteIdNotFound() {
        when(routeRepository.existsById(999L)).thenReturn(false);

        ResponseEntity<List<com.tech.orbi.dto.DeliveryDto>> response = deliveryController.getDeliveriesByRouteId(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // --- skipDelivery unit tests ---

    @Test
    void testSkipDeliverySuccessForAssignedDriverWithDetailsCompletesRoute() {
        route.setStatus(RouteStatus.IN_PROGRESS);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Delivery skippedDelivery = new Delivery();
        skippedDelivery.setId(1L);
        skippedDelivery.setStatus(DeliveryStatus.SKIPPED);
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(skippedDelivery));

        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseEntity<Void> response = deliveryController.skipDelivery(
                1L, new DeliveryDetailsDto("  Cliente ausente  "), driverAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.SKIPPED, delivery.getStatus());
        assertEquals("Cliente ausente", delivery.getDetails());
        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertNotNull(route.getFinalizedAt());
        verify(deliveryRepository).save(delivery);
        verify(routeRepository).save(route);
    }

    @Test
    void testSkipDeliveryWithBlankDetailsMovesAssignedRouteToInProgress() {
        route.setStatus(RouteStatus.ASSIGNED);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Delivery pendingSecond = new Delivery();
        pendingSecond.setId(2L);
        pendingSecond.setStatus(DeliveryStatus.TO_BE_DELIVERED);
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(delivery, pendingSecond));

        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseEntity<Void> response = deliveryController.skipDelivery(
                1L, new DeliveryDetailsDto("   "), driverAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.SKIPPED, delivery.getStatus());
        assertNull(delivery.getDetails());
        assertEquals(RouteStatus.IN_PROGRESS, route.getStatus());
        assertNull(route.getFinalizedAt());
        verify(routeRepository).save(route);
    }

    @Test
    void testSkipDeliveryForbiddenForUnassignedDriver() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Authentication otherDriverAuth = mockJwtAuth(UUID.randomUUID().toString(), "SCOPE_DRIVER");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(1L, new DeliveryDetailsDto("Ausente"), otherDriverAuth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void testSkipDeliveryNotFound() {
        when(deliveryRepository.findById(999L)).thenReturn(Optional.empty());
        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(999L, null, driverAuth));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testSkipDeliveryWhenAlreadyDeliveredCancelledOrSkippedThrowsBadRequest() {
        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        for (DeliveryStatus terminal : List.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED, DeliveryStatus.SKIPPED)) {
            delivery.setStatus(terminal);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> deliveryController.skipDelivery(1L, null, driverAuth));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    // --- cancelDelivery unit tests ---

    @Test
    void testCancelDeliverySuccessForAssignedDriverWithDetails() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Delivery pendingSecond = new Delivery();
        pendingSecond.setId(2L);
        pendingSecond.setStatus(DeliveryStatus.TO_BE_DELIVERED);
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(delivery, pendingSecond));

        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseEntity<Void> response = deliveryController.cancelDelivery(
                1L, new DeliveryDetailsDto("  Recusado  "), driverAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
        assertEquals("Recusado", delivery.getDetails());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testCancelDeliveryForbiddenForUnassignedDriverEvenWhenRouteHasNoDriver() {
        route.setDriver(null);
        route.setCreatedBy(null);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication otherDriverAuth = mockJwtAuth(UUID.randomUUID().toString(), "SCOPE_DRIVER");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.cancelDelivery(1L, null, otherDriverAuth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    void testCancelDeliveryAllowedForRouteCreatorWhenRouteHasNoDriver() {
        UUID creatorId = UUID.randomUUID();
        User creator = new User();
        creator.setId(creatorId);
        route.setDriver(null);
        route.setCreatedBy(creator);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication creatorAuth = mockJwtAuth(creatorId.toString(), "SCOPE_BASIC");

        ResponseEntity<Void> response = deliveryController.cancelDelivery(1L, null, creatorAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.CANCELLED, delivery.getStatus());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void testCancelDeliveryWhenAlreadyCancelledOrSkippedThrowsBadRequest() {
        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        for (DeliveryStatus terminal : List.of(DeliveryStatus.CANCELLED, DeliveryStatus.SKIPPED)) {
            delivery.setStatus(terminal);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> deliveryController.cancelDelivery(1L, null, driverAuth));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Test
    void testCancelDeliveryNotFound() {
        when(deliveryRepository.findById(999L)).thenReturn(Optional.empty());
        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.cancelDelivery(999L, null, driverAuth));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- deliverStop & ownership/JWT edge cases ---

    @Test
    void testDeliverStopSuccessForAssignedDriverCompletesRoute() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(delivery));

        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");

        ResponseEntity<Void> response = deliveryController.deliverStop(1L, driverAuth);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(DeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getDeliveredAt());
        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertNotNull(route.getFinalizedAt());
        verify(deliveryRepository).save(delivery);
        verify(routeRepository).save(route);
    }

    @Test
    void testDeliverStopThrowsBadRequestWhenRouteHasNoDriverEvenForAdmin() {
        route.setDriver(null);
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        Authentication adminAuth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_ADMIN"))).when(adminAuth).getAuthorities();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.deliverStop(1L, adminAuth));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testDeliverStopForbiddenForUnassignedDriver() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));
        Authentication otherDriverAuth = mockJwtAuth(UUID.randomUUID().toString(), "SCOPE_DRIVER");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> deliveryController.deliverStop(1L, otherDriverAuth));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testDeliverStopWhenAlreadyFinalizedThrowsBadRequest() {
        Authentication driverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        for (DeliveryStatus terminal : List.of(DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED, DeliveryStatus.SKIPPED)) {
            delivery.setStatus(terminal);
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> deliveryController.deliverStop(1L, driverAuth));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
    }

    @Test
    void testOwnershipFailsClosedForNullAuthNonJwtMalformedSubjectAndNullRoute() {
        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        // 1. Null authentication
        ResponseStatusException nullAuthEx = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(1L, null, null));
        assertEquals(HttpStatus.FORBIDDEN, nullAuthEx.getStatusCode());

        // 2. Non-Jwt principal
        Authentication nonJwtAuth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("SCOPE_DRIVER"))).when(nonJwtAuth).getAuthorities();
        doReturn("string-principal").when(nonJwtAuth).getPrincipal();
        ResponseStatusException nonJwtEx = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(1L, null, nonJwtAuth));
        assertEquals(HttpStatus.FORBIDDEN, nonJwtEx.getStatusCode());

        // 3. Malformed subject
        Authentication malformedAuth = mockJwtAuth("not-a-uuid", "SCOPE_DRIVER");
        ResponseStatusException malformedEx = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(1L, null, malformedAuth));
        assertEquals(HttpStatus.FORBIDDEN, malformedEx.getStatusCode());

        // 4. Null subject
        Authentication nullSubAuth = mockJwtAuth(null, "SCOPE_DRIVER");
        ResponseStatusException nullSubEx = assertThrows(ResponseStatusException.class,
                () -> deliveryController.cancelDelivery(1L, null, nullSubAuth));
        assertEquals(HttpStatus.FORBIDDEN, nullSubEx.getStatusCode());

        // 5. Orphan delivery (route == null) for non-admin
        delivery.setRoute(null);
        Authentication validDriverAuth = mockJwtAuth(driverId.toString(), "SCOPE_DRIVER");
        ResponseStatusException orphanEx = assertThrows(ResponseStatusException.class,
                () -> deliveryController.skipDelivery(1L, null, validDriverAuth));
        assertEquals(HttpStatus.FORBIDDEN, orphanEx.getStatusCode());
    }

    private Authentication mockJwtAuth(String subject, String scopeAuthority) {
        Authentication auth = mock(Authentication.class);
        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(subject);
        lenient().doReturn(jwt).when(auth).getPrincipal();
        lenient().doReturn(List.of(new SimpleGrantedAuthority(scopeAuthority))).when(auth).getAuthorities();
        return auth;
    }
}
