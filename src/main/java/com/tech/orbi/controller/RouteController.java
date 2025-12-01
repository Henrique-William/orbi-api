package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.DeliveryDto;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteResponseDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.User; // Importação da entidade User
import com.tech.orbi.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional; // Importação do Optional
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;

    public RouteController(RouteService routeService, RouteRepository routeRepository, DeliveryRepository deliveryRepository, UserRepository userRepository) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @PostMapping("/optimize")
    public ResponseEntity<List<LocationDto>> postBestRoute(
            @RequestBody List<LocationDto> locations,
            @RequestParam(defaultValue = "0") int startIndex) {

        if (locations == null || locations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Extrai o driverId do primeiro LocationDto na lista
        UUID driverIdFromRequest = locations.get(0).driverId();

        // 2. Busca a entidade User (Driver) no banco de dados UMA VEZ usando o UUID
        Optional<User> driverOptional = Optional.empty();
        if (driverIdFromRequest != null) {
            driverOptional = userRepository.findById(driverIdFromRequest);
        }

        // 3. Se o ID foi fornecido, mas o motorista não foi encontrado, retorna 404
        if (driverIdFromRequest != null && driverOptional.isEmpty()) {
            System.out.println("Driver com ID: " + driverIdFromRequest + " não encontrado.");
            return ResponseEntity.notFound().build();
        }

        // O objeto User (Driver) a ser atrelado
        User driver = driverOptional.orElse(null);

        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, startIndex);

        // Salvando a rota
        Route newRoute = new Route();
         if (driver != null) {
             newRoute.setDriver(driver);
         }

        newRoute = routeRepository.save(newRoute);

        for (int i = 0; i < optimizedRoute.size(); i++) {
            LocationDto location = optimizedRoute.get(i);
            Delivery delivery = new Delivery();

            // Atribuições da Delivery
            delivery.setRoute(newRoute);
            delivery.setOrder(i + 1);

            delivery.setDropoffAddress(location.address());
            delivery.setDropoffLatitude(BigDecimal.valueOf(location.latitude()));
            delivery.setDropoffLongitude(BigDecimal.valueOf(location.longitude()));

            delivery.setRecipientName(location.recipientName());
            delivery.setRecipientPhone(location.recipientPhone());
            delivery.setRecipientEmail(location.recipientEmail());
            delivery.setPackageDetails(location.packageDetails());

            // 4. Atrela o objeto User (Driver) à Delivery (se encontrado)
            if (driver != null) {
                delivery.setDriver(driver);
            }

            deliveryRepository.save(delivery);
        }

        return ResponseEntity.ok(optimizedRoute);
    }

    @GetMapping
    public ResponseEntity<List<RouteResponseDto>> getAllRoutes() {
        var routes = routeRepository.findAll();

        var response = routes.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponseDto> getRouteById(@PathVariable Integer id) {
        return routeRepository.findById(id)
                .map(route -> ResponseEntity.ok(toDto(route)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Void> deleteRoute(@PathVariable Integer id) {
        if (!routeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        routeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private RouteResponseDto toDto(Route route) {
        List<DeliveryDto> deliveryDtos = route.getDeliveries() != null
                ? route.getDeliveries().stream().map(this::toDeliveryDto).toList()
                : List.of();

        return new RouteResponseDto(
                route.getId(),
                route.getDriver() != null ? route.getDriver().getId() : null,
                route.getDriver() != null ? route.getDriver().getName() : "No Driver",
                deliveryDtos // Passa a lista convertida, que não tem o ciclo infinito
        );
    }

    // Metodo auxiliar para converter Delivery -> DTO
    private DeliveryDto toDeliveryDto(Delivery delivery) {
        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder(),
                delivery.getStatus(),
                delivery.getRecipientName(),
                delivery.getDropoffAddress(),
                delivery.getPackageDetails()
        );
    }

}