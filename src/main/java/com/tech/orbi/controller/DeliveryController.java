package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.DeliveryStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryRepository deliveryRepository;

    public DeliveryController(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @PatchMapping("/{id}/next-status")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_BASIC') or hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Void> nextStatus(@PathVariable Integer id, Authentication authentication) {

        var delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));

        // 2. Validação de segurança: O motorista é dono da entrega?
        validateDriverOwnership(delivery, authentication);

        switch (delivery.getStatus()) {
            case AT_PICKUP -> delivery.setStatus(DeliveryStatus.IN_TRANSIT);
            case IN_TRANSIT -> {
                delivery.setStatus(DeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(LocalDateTime.now());
            }
            case DELIVERED, CANCELLED ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A entrega já foi finalizada.");
            default -> {
                if (delivery.getStatus() == DeliveryStatus.REQUESTED || delivery.getStatus() == DeliveryStatus.ACCEPTED) {
                    delivery.setStatus(DeliveryStatus.AT_PICKUP);
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transição de status inválida.");
                }
            }
        }

        deliveryRepository.save(delivery);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    @PreAuthorize("hasAuthority('SCOPE_BASIC') or hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Void> cancelDelivery(@PathVariable Integer id, Authentication authentication) {

        var delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));

        // Validação de segurança
        validateDriverOwnership(delivery, authentication);

        if (delivery.getStatus() == DeliveryStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível cancelar uma entrega já finalizada.");
        }

        delivery.setStatus(DeliveryStatus.CANCELLED);
        deliveryRepository.save(delivery);

        return ResponseEntity.noContent().build();
    }

    private void validateDriverOwnership(Delivery delivery, Authentication authentication) {
        // Se for ADMIN, passa direto
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("SCOPE_ADMIN"));

        if (isAdmin) return;

        // Pega o UUID do usuário logado através do Token JWT
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID userIdFromToken = UUID.fromString(jwt.getSubject());

        // Verifica se a entrega tem motorista e se o motorista é o usuário logado
        if (delivery.getDriver() == null || !delivery.getDriver().getId().equals(userIdFromToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não tem permissão para alterar esta entrega.");
        }
    }
}