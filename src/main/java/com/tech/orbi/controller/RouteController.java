package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.DeliveryDto;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteResponseDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.Driver;
import com.tech.orbi.entity.Route;
import com.tech.orbi.service.RouteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    private final RouteService routeService;
    private final RouteRepository routeRepository;
    private final DeliveryRepository deliveryRepository;
    private final DriverRepository driverRepository;

    public RouteController(RouteService routeService,
                           RouteRepository routeRepository,
                           DeliveryRepository deliveryRepository,
                           DriverRepository driverRepository) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
    }

    @Transactional
    @PostMapping("/optimize")
    public ResponseEntity<List<LocationDto>> postBestRoute(
            @RequestBody List<LocationDto> locations,
            @RequestParam(defaultValue = "0") int startIndex) {

        if (locations == null || locations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID driverIdFromRequest = locations.getFirst().driverId();
        Optional<Driver> driverProfileOptional = Optional.empty();

        if (driverIdFromRequest != null) {
            driverProfileOptional = driverRepository.findById(driverIdFromRequest);
        }

        if (driverIdFromRequest != null && driverProfileOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Driver driver = driverProfileOptional.orElse(null);
        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, startIndex);

        Route newRoute = new Route();
        if (driver != null) {
            newRoute.setAssignedDriver(driver);
        }

        newRoute = routeRepository.save(newRoute);

        for (int i = 0; i < optimizedRoute.size(); i++) {
            LocationDto location = optimizedRoute.get(i);
            Delivery delivery = new Delivery();
            delivery.setRoute(newRoute);
            delivery.setOrder(i + 1);
            delivery.setAddress(location.address());
            delivery.setLatitude(BigDecimal.valueOf(location.latitude()));
            delivery.setLongitude(BigDecimal.valueOf(location.longitude()));
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

    /**
     * CORREÇÃO: Mapeia corretamente as entidades seguindo as posições exatas e os tipos
     * exigidos no construtor do record RouteResponseDto. Além disso, previne o NullPointerException
     * checando se o motorista (Driver) é nulo antes de buscar o seu nome.
     */
    private RouteResponseDto toDto(Route route) {
        List<DeliveryDto> deliveryDtos = route.getDeliveries() != null
                ? route.getDeliveries().stream().map(this::toDeliveryDto).toList()
                : List.of();

        Driver driver = route.getAssignedDriver();
        String driverName = driver != null ? driver.getDriverName() : null;

        return new RouteResponseDto(
                route.getId(),
                driver,                    // 2º parâmetro (Driver driverId)
                route.getGenereatedBy(),   // 3º parâmetro (User generatedById - usando a grafia da classe Route)
                driverName,                // 4º parâmetro (String driverName)
                route.getCreatedAt(),      // 5º parâmetro (LocalDateTime createdAt)
                deliveryDtos               // 6º parâmetro (List<DeliveryDto> deliveries)
        );
    }

    private DeliveryDto toDeliveryDto(Delivery delivery) {
        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder(),
                delivery.getStatus(),
                delivery.getAddress(),
                delivery.getDeliveredAt()
        );
    }
}