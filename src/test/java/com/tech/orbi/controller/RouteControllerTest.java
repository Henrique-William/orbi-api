package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteAssignDriverDto;
import com.tech.orbi.dto.RouteOptimizeRequestDto;
import com.tech.orbi.dto.RouteResponseDto;
import com.tech.orbi.entity.*;
import com.tech.orbi.service.RouteService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RouteController routeController;

    private UUID driverId;
    private Driver driver;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        driver = new Driver();
        driver.setUserId(driverId);
        driver.setDriverName("Motorista Teste");

        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(driverId.toString());
        lenient().doReturn(jwt).when(authentication).getPrincipal();
        lenient().doReturn(List.of(new SimpleGrantedAuthority("SCOPE_BASIC"))).when(authentication).getAuthorities();
    }

    @Test
    void testOptimizeRouteWithDriverIdAssignsDriverAndAssignedAt() {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6, "teste@email.com", "Destinatario", "19999", "Pacote 1");
        LocationDto loc2 = new LocationDto("Destino", -23.6, -46.7);
        List<LocationDto> locations = List.of(loc1, loc2);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto(driverId.toString(), locations);

        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(routeService.findBestRoute(locations, 0)).thenReturn(locations);

        ResponseEntity<List<LocationDto>> response = routeController.postBestRoute(request, 0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(routeService).saveOptimizedRoute(argThat(route -> {
            assertEquals(RouteStatus.ASSIGNED, route.getStatus());
            assertEquals(driver, route.getDriver());
            assertNotNull(route.getAssignedAt());
            assertEquals(2, route.getTotalStops());
            return true;
        }), argThat(deliveries -> {
            assertEquals(2, deliveries.size());
            assertEquals("Origem", deliveries.get(0).getAddress());
            assertEquals("teste@email.com", deliveries.get(0).getRecipientEmail());
            return true;
        }));
    }

    @Test
    void testOptimizeRouteWithInvalidDriverIdStringThrowsBadRequest() {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto("3959", List.of(loc1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> routeController.postBestRoute(request, 0));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("UUID válido no formato padrão de 36 caracteres"));
    }

    @Test
    void testOptimizeRouteWithoutDriverCreatesRouteAsCreatedWithNullAssignedAt() {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6);
        LocationDto loc2 = new LocationDto("Destino", -23.6, -46.7);
        List<LocationDto> locations = List.of(loc1, loc2);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto(null, locations);

        when(routeService.findBestRoute(locations, 0)).thenReturn(locations);

        ResponseEntity<List<LocationDto>> response = routeController.postBestRoute(request, 0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(routeService).saveOptimizedRoute(argThat(route -> {
            assertEquals(RouteStatus.CREATED, route.getStatus());
            assertNull(route.getDriver());
            assertNull(route.getAssignedAt());
            assertEquals(2, route.getTotalStops());
            return true;
        }), argThat(deliveries -> deliveries.size() == 2));
    }

    @Test
    void testOptimizeRouteWithInvalidDriverReturns404() {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto(driverId.toString(), List.of(loc1));

        when(driverRepository.findById(driverId)).thenReturn(Optional.empty());

        ResponseEntity<List<LocationDto>> response = routeController.postBestRoute(request, 0);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(routeService, never()).saveOptimizedRoute(any(), any());
    }

    @Test
    void testOptimizeRouteWithAuthenticationSetsCreatedBy() {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto(null, List.of(loc1));

        UUID creatorId = UUID.randomUUID();
        User creatorUser = new User();
        creatorUser.setId(creatorId);

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(creatorId.toString());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creatorUser));
        when(routeService.findBestRoute(any(), eq(0))).thenReturn(List.of(loc1));

        ResponseEntity<List<LocationDto>> response = routeController.postBestRoute(request, 0, auth, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(routeService).saveOptimizedRoute(argThat(route -> {
            assertNotNull(route.getCreatedBy());
            assertEquals(creatorId, route.getCreatedBy().getId());
            return true;
        }), any());
    }

    @Test
    void testOptimizeRouteWhenClientAbortedThrowsBadRequestAndNeverSaves() throws IOException {
        LocationDto loc1 = new LocationDto("Origem", -23.5, -46.6);
        RouteOptimizeRequestDto request = new RouteOptimizeRequestDto(null, List.of(loc1));

        when(routeService.findBestRoute(any(), eq(0))).thenReturn(List.of(loc1));

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IOException("Broken pipe")).when(outputStream).write(any(byte[].class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> routeController.postBestRoute(request, 0, null, response));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Requisição cancelada pelo cliente"));
        verify(routeService, never()).saveOptimizedRoute(any(), any());
    }

    @Test
    void testNextStatusRouteProgression() {
        Route route = new Route();
        route.setId(5L);
        route.setStatus(RouteStatus.ASSIGNED);
        route.setDriver(driver);

        when(routeRepository.findById(5L)).thenReturn(Optional.of(route));

        // ASSIGNED -> IN_PROGRESS
        ResponseEntity<Void> res1 = routeController.nextStatus(5L, null, authentication);
        assertEquals(HttpStatus.NO_CONTENT, res1.getStatusCode());
        assertEquals(RouteStatus.IN_PROGRESS, route.getStatus());

        // IN_PROGRESS -> COMPLETED
        ResponseEntity<Void> res2 = routeController.nextStatus(5L, null, authentication);
        assertEquals(HttpStatus.NO_CONTENT, res2.getStatusCode());
        assertEquals(RouteStatus.COMPLETED, route.getStatus());
        assertNotNull(route.getFinalizedAt());

        // COMPLETED -> BAD_REQUEST
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> routeController.nextStatus(5L, null, authentication));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testAssignDriverEndpointSuccess() {
        Route route = new Route();
        route.setId(5L);
        route.setStatus(RouteStatus.CREATED);

        when(routeRepository.findById(5L)).thenReturn(Optional.of(route));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));

        ResponseEntity<Void> response = routeController.assignDriver(5L, new RouteAssignDriverDto(driverId.toString()), authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(RouteStatus.ASSIGNED, route.getStatus());
        assertEquals(driver, route.getDriver());
        assertNotNull(route.getAssignedAt());
        verify(routeRepository).save(route);
    }

    @Test
    void testCancelRouteSuccess() {
        Route route = new Route();
        route.setId(5L);
        route.setStatus(RouteStatus.ASSIGNED);
        route.setDriver(driver);

        Delivery d1 = new Delivery();
        d1.setId(1L);
        d1.setStatus(DeliveryStatus.TO_BE_DELIVERED);

        Delivery d2 = new Delivery();
        d2.setId(2L);
        d2.setStatus(DeliveryStatus.IN_TRANSIT);

        Delivery d3 = new Delivery();
        d3.setId(3L);
        d3.setStatus(DeliveryStatus.DELIVERED);

        when(routeRepository.findById(5L)).thenReturn(Optional.of(route));
        when(deliveryRepository.findByRouteId(5L)).thenReturn(List.of(d1, d2, d3));

        ResponseEntity<Void> response = routeController.cancelRoute(5L, authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(RouteStatus.CANCELLED, route.getStatus());
        verify(routeRepository).save(route);

        assertEquals(DeliveryStatus.CANCELLED, d1.getStatus());
        assertEquals(DeliveryStatus.CANCELLED, d2.getStatus());
        assertEquals(DeliveryStatus.DELIVERED, d3.getStatus());
        verify(deliveryRepository).saveAll(List.of(d1, d2, d3));
    }

    @Test
    void testGetRouteByIdReturnsDtoWithoutDeliveries() {
        Route route = new Route();
        route.setId(10L);
        route.setStatus(RouteStatus.ASSIGNED);
        route.setDriver(driver);
        route.setTotalStops(5);

        when(routeRepository.findById(10L)).thenReturn(Optional.of(route));

        ResponseEntity<RouteResponseDto> response = routeController.getRouteById(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().id());
        assertEquals(5, response.getBody().totalStops());
    }

    @Test
    void testGetRouteDeliveriesSuccess() {
        Delivery d1 = new Delivery();
        d1.setId(101L);
        d1.setOrder(1);
        d1.setAddress("Rua Teste, 100");
        d1.setStatus(DeliveryStatus.TO_BE_DELIVERED);

        when(routeRepository.existsById(10L)).thenReturn(true);
        when(deliveryRepository.findByRouteId(10L)).thenReturn(List.of(d1));

        ResponseEntity<List<LocationDto>> responsePlaceholder; // check return type
        var response = routeController.getRouteDeliveries(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(101L, response.getBody().get(0).id());
        assertEquals(1, response.getBody().get(0).order());
        assertEquals("Rua Teste, 100", response.getBody().get(0).address());
    }

    @Test
    void testGetRouteDeliveriesNotFound() {
        when(routeRepository.existsById(999L)).thenReturn(false);

        var response = routeController.getRouteDeliveries(999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
