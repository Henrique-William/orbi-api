package com.tech.orbi.service;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.LocationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private OpenRouteServiceClient orsClient;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeService = new RouteService(orsClient, routeRepository, deliveryRepository, false);
    }

    @Test
    void testFindBestRouteSingleLocation() {
        LocationDto loc = new LocationDto("Origem", -22.90, -47.06);
        List<LocationDto> result = routeService.findBestRoute(List.of(loc), 0);
        assertEquals(1, result.size());
        assertEquals("Origem", result.get(0).address());
    }

    @Test
    void testFindBestRouteTwoLocations() {
        LocationDto loc1 = new LocationDto("Origem", -22.90, -47.06);
        LocationDto loc2 = new LocationDto("Destino", -22.91, -47.05);

        List<LocationDto> result = routeService.findBestRoute(List.of(loc1, loc2), 0);
        assertEquals(3, result.size());
        assertEquals("Origem", result.get(0).address());
        assertEquals("Destino", result.get(1).address());
        assertEquals("Origem", result.get(2).address());
    }

    @Test
    void testFindBestRouteWithImprovedAlns() {
        List<LocationDto> locations = List.of(
                new LocationDto("Depósito Central", -22.904, -47.060),
                new LocationDto("Cliente 1 - Cambuí", -22.900, -47.054),
                new LocationDto("Cliente 2 - Barão Geraldo", -22.822, -47.080),
                new LocationDto("Cliente 3 - Taquaral", -22.871, -47.045),
                new LocationDto("Cliente 4 - Guanabara", -22.888, -47.062)
        );

        int n = locations.size();
        double[][] distances = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) distances[i][j] = 0.0;
                else distances[i][j] = Math.abs(i - j) * 3.5;
            }
        }

        when(orsClient.getDistanceMatrix(anyList())).thenReturn(distances);

        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, 0);

        assertNotNull(optimizedRoute);
        // O circuito fechado deve ter n + 1 paradas
        assertEquals(n + 1, optimizedRoute.size());

        // Deve iniciar e terminar no startIndex (0: Depósito Central)
        assertEquals("Depósito Central", optimizedRoute.get(0).address());
        assertEquals("Depósito Central", optimizedRoute.get(optimizedRoute.size() - 1).address());

        // Todas as paradas intermediárias devem ter sido visitadas
        for (LocationDto loc : locations) {
            assertTrue(optimizedRoute.stream().anyMatch(l -> l.address().equals(loc.address())),
                    "A parada '" + loc.address() + "' deve estar contida na rota otimizada.");
        }
    }

    @Test
    void testValidationFailsForInvalidStartIndex() {
        List<LocationDto> locations = List.of(
                new LocationDto("Origem", -22.90, -47.06),
                new LocationDto("Destino", -22.91, -47.05)
        );

        assertThrows(IllegalArgumentException.class, () -> routeService.findBestRoute(locations, -1));
        assertThrows(IllegalArgumentException.class, () -> routeService.findBestRoute(locations, 5));
    }

    @Test
    void testValidationFailsForInvalidCoordinates() {
        List<LocationDto> locations = List.of(
                new LocationDto("Inválido", 120.0, -47.06) // Latitude > 90
        );

        assertThrows(IllegalArgumentException.class, () -> routeService.findBestRoute(locations, 0));
    }
}
