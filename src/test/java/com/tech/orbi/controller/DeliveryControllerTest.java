package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
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

        ResponseEntity<Void> response = deliveryController.cancelDelivery(1L, authentication);

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
                () -> deliveryController.cancelDelivery(1L, authentication));

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

        ResponseEntity<Void> response = deliveryController.cancelDelivery(1L, adminAuth);

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
}
