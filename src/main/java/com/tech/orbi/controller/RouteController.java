package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverProfileRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.DeliveryDto;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteResponseDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DriverProfile;
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
    private final DriverProfileRepository driverProfileRepository;

    public RouteController(RouteService routeService, RouteRepository routeRepository, DeliveryRepository deliveryRepository, DriverProfileRepository driverProfileRepository) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.driverProfileRepository = driverProfileRepository;
    }

    @Transactional
    @PostMapping("/optimize")
    public ResponseEntity<List<LocationDto>> postBestRoute(
            @RequestBody List<LocationDto> locations,
            @RequestParam(defaultValue = "0") int startIndex) {

        if (locations == null || locations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        UUID driverIdFromRequest = locations.get(0).driverId();

        Optional<DriverProfile> driverProfileOptional = Optional.empty();
        if (driverIdFromRequest != null) {
            driverProfileOptional = driverProfileRepository.findById(driverIdFromRequest);
        }

        if (driverIdFromRequest != null && driverProfileOptional.isEmpty()) {
            System.out.println("Driver Profile com ID: " + driverIdFromRequest + " não encontrado.");
            return ResponseEntity.notFound().build();
        }

        DriverProfile driverProfile = driverProfileOptional.orElse(null);

        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, startIndex);

        Route newRoute = new Route();
        if (driverProfile != null) {
            newRoute.setDriverProfile(driverProfile);
        }

        newRoute = routeRepository.save(newRoute);

        for (int i = 0; i < optimizedRoute.size(); i++) {
            LocationDto location = optimizedRoute.get(i);
            Delivery delivery = new Delivery();

            delivery.setRoute(newRoute);
            delivery.setOrder(i + 1);

            delivery.setDropoffAddress(location.address());
            delivery.setDropoffLatitude(BigDecimal.valueOf(location.latitude()));
            delivery.setDropoffLongitude(BigDecimal.valueOf(location.longitude()));

            delivery.setRecipientName(location.recipientName());
            delivery.setRecipientPhone(location.recipientPhone());
            delivery.setRecipientEmail(location.recipientEmail());
            delivery.setPackageDetails(location.packageDetails());

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

        DriverProfile driverProfile = route.getDriverProfile();
        UUID driverId = driverProfile != null ? driverProfile.getUserId() : null;
        String driverName = driverProfile != null && driverProfile.getUser() != null ? driverProfile.getUser().getName() : "No Driver";


        return new RouteResponseDto(
                route.getId(),
                driverId,
                driverName,
                route.getCreatedAt(),
                deliveryDtos
        );
    }

    private DeliveryDto toDeliveryDto(Delivery delivery) {
        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder(),
                delivery.getStatus(),
                delivery.getRecipientName(),
                delivery.getDropoffAddress(),
                delivery.getPackageDetails(),
                delivery.getDeliveredAt()
        );
    }

}