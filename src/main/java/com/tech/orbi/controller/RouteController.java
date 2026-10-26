package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.DriverRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.*;
import com.tech.orbi.entity.*;
import com.tech.orbi.service.RouteService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final UserRepository userRepository;

    public RouteController(RouteService routeService,
                           RouteRepository routeRepository,
                           DeliveryRepository deliveryRepository,
                           DriverRepository driverRepository,
                           UserRepository userRepository) {
        this.routeService = routeService;
        this.routeRepository = routeRepository;
        this.deliveryRepository = deliveryRepository;
        this.driverRepository = driverRepository;
        this.userRepository = userRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/optimize")
    public ResponseEntity<List<LocationDto>> postBestRoute(
            @RequestBody RouteOptimizeRequestDto request,
            @RequestParam(defaultValue = "0") int startIndex,
            Authentication authentication,
            HttpServletResponse response) {

        if (request == null || request.locations() == null || request.locations().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<LocationDto> locations = request.locations();
        String rawDriverId = request.driverId();
        UUID driverIdFromRequest = null;
        if (rawDriverId != null && !rawDriverId.isBlank() && !"null".equalsIgnoreCase(rawDriverId.trim())) {
            try {
                driverIdFromRequest = UUID.fromString(rawDriverId.trim());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O campo driverId deve ser um UUID válido no formato padrão de 36 caracteres (ex: 'a5e8f49a-6a2c-47bc-a521-4d10df3a4bc0'). Valor recebido: '" + rawDriverId + "'");
            }
        }

        Optional<Driver> driverProfileOptional = Optional.empty();

        if (driverIdFromRequest != null) {
            driverProfileOptional = driverRepository.findById(driverIdFromRequest);
            if (driverProfileOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
        }

        Driver driver = driverProfileOptional.orElse(null);
        List<LocationDto> optimizedRoute = routeService.findBestRoute(locations, startIndex);

        if (response != null) {
            try {
                response.getOutputStream().write(new byte[0]);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requisição cancelada pelo cliente.");
            }
        }

        Route newRoute = new Route();
        if (driver != null) {
            newRoute.setDriver(driver);
            newRoute.setStatus(RouteStatus.ASSIGNED);
            newRoute.setAssignedAt(LocalDateTime.now());
        } else {
            newRoute.setStatus(RouteStatus.CREATED);
            newRoute.setAssignedAt(null);
        }
        newRoute.setTotalStops(optimizedRoute.size());

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            try {
                UUID userId = UUID.fromString(jwt.getSubject());
                userRepository.findById(userId).ifPresent(newRoute::setCreatedBy);
            } catch (Exception ignored) {
            }
        }

        List<Delivery> deliveries = new ArrayList<>();
        for (int i = 0; i < optimizedRoute.size(); i++) {
            LocationDto location = optimizedRoute.get(i);
            Delivery delivery = new Delivery();
            delivery.setOrder(i + 1);
            delivery.setAddress(location.address());
            delivery.setLatitude(BigDecimal.valueOf(location.latitude()));
            delivery.setLongitude(BigDecimal.valueOf(location.longitude()));
            if (location.recipientEmail() != null) {
                delivery.setRecipientEmail(location.recipientEmail());
            }
            deliveries.add(delivery);
        }

        routeService.saveOptimizedRoute(newRoute, deliveries);

        return ResponseEntity.ok(optimizedRoute);
    }

    public ResponseEntity<List<LocationDto>> postBestRoute(
            RouteOptimizeRequestDto request,
            int startIndex) {
        return postBestRoute(request, startIndex, null, null);
    }

    @PatchMapping("/{id}/next-status")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_DRIVER') or hasAuthority('SCOPE_ADMIN') or hasAuthority('SCOPE_BASIC')")
    public ResponseEntity<Void> nextStatus(
            @PathVariable Long id,
            @RequestBody(required = false) RouteAssignDriverDto body,
            Authentication authentication) {

        var route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada"));

        validateRouteOwnership(route, authentication);

        switch (route.getStatus()) {
            case CREATED -> {
                String rawId = (body != null && body.driverId() != null)
                        ? body.driverId()
                        : (route.getDriver() != null ? route.getDriver().getUserId().toString() : null);

                if (rawId == null || rawId.isBlank() || "null".equalsIgnoreCase(rawId.trim())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "É necessário atribuir um motorista para avançar para o status ASSIGNED.");
                }

                UUID driverId;
                try {
                    driverId = UUID.fromString(rawId.trim());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "O campo driverId deve ser um UUID válido no formato padrão de 36 caracteres. Valor recebido: '" + rawId + "'");
                }

                Driver driver = driverRepository.findById(driverId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado"));

                route.setDriver(driver);
                route.setAssignedAt(LocalDateTime.now());
                route.setStatus(RouteStatus.ASSIGNED);
            }
            case ASSIGNED -> {
                route.setStatus(RouteStatus.IN_PROGRESS);
            }
            case IN_PROGRESS -> {
                route.setStatus(RouteStatus.COMPLETED);
                route.setFinalizedAt(LocalDateTime.now());
            }
            case COMPLETED, CANCELLED ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rota já foi finalizada.");
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transição de status inválida.");
        }

        routeRepository.save(route);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assign-driver")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_DRIVER') or hasAuthority('SCOPE_ADMIN') or hasAuthority('SCOPE_BASIC')")
    public ResponseEntity<Void> assignDriver(
            @PathVariable Long id,
            @RequestBody RouteAssignDriverDto body,
            Authentication authentication) {

        if (body == null || body.driverId() == null || body.driverId().isBlank() || "null".equalsIgnoreCase(body.driverId().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "driverId é obrigatório.");
        }

        UUID driverId;
        try {
            driverId = UUID.fromString(body.driverId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O campo driverId deve ser um UUID válido no formato padrão de 36 caracteres. Valor recebido: '" + body.driverId() + "'");
        }

        var route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada"));

        validateRouteOwnership(route, authentication);

        if (route.getStatus() == RouteStatus.COMPLETED || route.getStatus() == RouteStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível alterar motorista de uma rota já finalizada.");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado"));

        route.setDriver(driver);
        route.setAssignedAt(LocalDateTime.now());
        if (route.getStatus() == RouteStatus.CREATED) {
            route.setStatus(RouteStatus.ASSIGNED);
        }

        routeRepository.save(route);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_DRIVER') or hasAuthority('SCOPE_ADMIN') or hasAuthority('SCOPE_BASIC')")
    public ResponseEntity<Void> cancelRoute(@PathVariable Long id, Authentication authentication) {

        var route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada"));

        validateRouteOwnership(route, authentication);

        if (route.getStatus() == RouteStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível cancelar uma rota já finalizada.");
        }

        if (route.getStatus() == RouteStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rota já está cancelada.");
        }

        route.setStatus(RouteStatus.CANCELLED);
        routeRepository.save(route);

        List<Delivery> deliveries = deliveryRepository.findByRouteId(route.getId());
        if (deliveries != null && !deliveries.isEmpty()) {
            for (Delivery delivery : deliveries) {
                if (delivery.getStatus() != DeliveryStatus.DELIVERED) {
                    delivery.setStatus(DeliveryStatus.CANCELLED);
                }
            }
            deliveryRepository.saveAll(deliveries);
        }

        return ResponseEntity.noContent().build();
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
    public ResponseEntity<RouteResponseDto> getRouteById(@PathVariable Long id) {
        return routeRepository.findById(id)
                .map(route -> ResponseEntity.ok(toDto(route)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/directions")
    public ResponseEntity<RouteDirectionsDto> getRouteDirections(@PathVariable Long id) {
        return ResponseEntity.ok(routeService.getDirections(id));
    }

    @Transactional
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long id) {
        if (!routeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        deliveryRepository.deleteByRouteId(id);
        routeRepository.deleteById(id);

        return ResponseEntity.noContent().build();
    }

    private void validateRouteOwnership(Route route, Authentication authentication) {
        if (authentication == null) return;
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_ADMIN"));

        if (isAdmin) return;

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            UUID userIdFromToken = UUID.fromString(jwt.getSubject());
            boolean isAssignedDriver = route.getDriver() != null && route.getDriver().getUserId().equals(userIdFromToken);
            boolean isCreator = route.getCreatedBy() != null && route.getCreatedBy().getId().equals(userIdFromToken);
            if (!isAssignedDriver && !isCreator && route.getStatus() != RouteStatus.CREATED) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para alterar esta rota.");
            }
        }
    }

    private RouteResponseDto toDto(Route route) {
        Driver driver = route.getDriver();
        String driverName = driver != null ? driver.getDriverName() : null;

        return new RouteResponseDto(
                route.getId(),
                driver,
                route.getCreatedBy() != null ? route.getCreatedBy().getId() : null,
                driverName,
                route.getStatus(),
                route.getTotalDistance(),
                route.getTotalCost(),
                route.getTotalStops(),
                route.getCreatedAt(),
                route.getAssignedAt(),
                route.getFinalizedAt()
        );
    }

    private DeliveryDto toDeliveryDto(Delivery delivery) {
        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder(),
                delivery.getStatus(),
                delivery.getAddress(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt()
        );
    }

}
