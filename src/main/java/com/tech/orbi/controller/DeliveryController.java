package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.DeliveryDto;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import com.tech.orbi.entity.Route;
import com.tech.orbi.entity.RouteStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;
    private final RouteRepository routeRepository;

    public DeliveryController(DeliveryRepository deliveryRepository, RouteRepository routeRepository) {
        this.deliveryRepository = deliveryRepository;
        this.routeRepository = routeRepository;
    }

    @PatchMapping("/{id}/next-status")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_DRIVER') or hasAuthority('SCOPE_ADMIN') or hasAuthority('SCOPE_BASIC')")
    public ResponseEntity<Void> nextStatus(@PathVariable Long id, Authentication authentication) {

        var delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));

        // Validação de segurança: O usuário/motorista tem permissão para esta entrega?
        validateDriverOwnership(delivery, authentication);

        if (delivery.getRoute() == null || delivery.getRoute().getDriver() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível avançar o status da entrega pois a rota não possui motorista atribuído.");
        }

        switch (delivery.getStatus()) {
            case TO_BE_DELIVERED -> {
                delivery.setStatus(DeliveryStatus.IN_TRANSIT);
                delivery.setPickedUpAt(LocalDateTime.now());
            }
            case IN_TRANSIT -> {
                delivery.setStatus(DeliveryStatus.OUT_FOR_DELIVERY);
            }
            case OUT_FOR_DELIVERY -> {
                delivery.setStatus(DeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(LocalDateTime.now());
            }
            case DELIVERED, CANCELLED ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A entrega já foi finalizada.");
            default ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transição de status inválida.");
        }

        deliveryRepository.save(delivery);

        updateRouteStatusOnDeliveryChange(delivery.getRoute());

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_DRIVER') or hasAuthority('SCOPE_ADMIN') or hasAuthority('SCOPE_BASIC')")
    public ResponseEntity<Void> cancelDelivery(@PathVariable Long id, Authentication authentication) {

        var delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));

        // Validação de segurança
        validateDriverOwnership(delivery, authentication);

        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível cancelar uma entrega já finalizada.");
        }

        if (delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A entrega já está cancelada.");
        }

        delivery.setStatus(DeliveryStatus.CANCELLED);
        deliveryRepository.save(delivery);

        updateRouteStatusOnDeliveryChange(delivery.getRoute());

        return ResponseEntity.noContent().build();
    }

    private void updateRouteStatusOnDeliveryChange(Route route) {
        if (route == null || route.getId() == null) return;
        if (route.getStatus() == RouteStatus.COMPLETED || route.getStatus() == RouteStatus.CANCELLED) {
            return;
        }

        List<Delivery> deliveries = deliveryRepository.findByRouteId(route.getId());
        if (deliveries != null && !deliveries.isEmpty()) {
            boolean allFinalized = deliveries.stream()
                    .allMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.CANCELLED);

            if (allFinalized) {
                route.setStatus(RouteStatus.COMPLETED);
                route.setFinalizedAt(LocalDateTime.now());
                routeRepository.save(route);
            } else if (route.getStatus() == RouteStatus.ASSIGNED || (route.getStatus() == RouteStatus.CREATED && route.getDriver() != null)) {
                route.setStatus(RouteStatus.IN_PROGRESS);
                routeRepository.save(route);
            }
        }
    }

    private void validateDriverOwnership(Delivery delivery, Authentication authentication) {
        if (authentication == null) return;
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_ADMIN"));

        if (isAdmin) return;

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            UUID userIdFromToken = UUID.fromString(jwt.getSubject());

            var route = delivery.getRoute();
            boolean isAssignedDriver = route != null && route.getDriver() != null && route.getDriver().getUserId().equals(userIdFromToken);
            boolean isCreator = route != null && route.getCreatedBy() != null && route.getCreatedBy().getId().equals(userIdFromToken);

            if (!isAssignedDriver && !isCreator) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para alterar esta entrega.");
            }
        }
    }

    @GetMapping("/route/{routeId}")
    public ResponseEntity<List<DeliveryDto>> getDeliveriesByRouteId(@PathVariable Long routeId) {
        if (!routeRepository.existsById(routeId)) {
            return ResponseEntity.notFound().build();
        }

        var deliveries = deliveryRepository.findByRouteId(routeId);
        var response = deliveries.stream()
                .map(this::toDeliveryDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    private DeliveryDto toDeliveryDto(Delivery delivery) {
        return new DeliveryDto(
                delivery.getId(),
                delivery.getOrder(),
                delivery.getStatus(),
                delivery.getAddress(),
                delivery.getLatitude(),
                delivery.getLongitude(),
                delivery.getRecipientEmail(),
                delivery.getPickedUpAt(),
                delivery.getDeliveredAt()
        );
    }
}
