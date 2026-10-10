package com.tech.orbi.controller;

import com.tech.orbi.Repository.DeliveryRepository;
import com.tech.orbi.Repository.RatingRepository;
import com.tech.orbi.Repository.UserRepository;
import com.tech.orbi.dto.CreateRatingDto;
import com.tech.orbi.entity.Rating;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingRepository ratingRepository;
    private final DeliveryRepository deliveryRepository;
    private final UserRepository userRepository;

    public RatingController(RatingRepository ratingRepository,
                            DeliveryRepository deliveryRepository,
                            UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.deliveryRepository = deliveryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_BASIC') or hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Rating> createRating(@RequestBody CreateRatingDto dto, Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        UUID raterUserId = UUID.fromString(jwt.getSubject());

        var rater = userRepository.findById(raterUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário avaliador não encontrado"));

        var rated = userRepository.findById(dto.ratedUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário avaliado não encontrado"));

        var delivery = deliveryRepository.findById(dto.deliveryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));

        if (dto.ratingValue() < 1 || dto.ratingValue() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nota deve ser entre 1 e 5");
        }

        Rating rating = new Rating();
        rating.setRater(rater);
        rating.setRated(rated);
        rating.setDelivery(delivery);
        rating.setRatingValue(dto.ratingValue());
        rating.setComment(dto.comment());

        Rating saved = ratingRepository.save(rating);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rating> getRatingById(@PathVariable Integer id) {
        return ratingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/delivery/{deliveryId}")
    public ResponseEntity<List<Rating>> getRatingsByDelivery(@PathVariable Long deliveryId) {
        var delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrega não encontrada"));
        // Retorna todas as avaliações — filtro simples via stream
        List<Rating> ratings = ratingRepository.findAll().stream()
                .filter(r -> r.getDelivery().getId().equals(deliveryId))
                .toList();
        return ResponseEntity.ok(ratings);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Rating>> getRatingsByUser(@PathVariable UUID userId) {
        List<Rating> ratings = ratingRepository.findByRatedId(userId);
        return ResponseEntity.ok(ratings);
    }

    @Transactional
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_ADMIN')")
    public ResponseEntity<Void> deleteRating(@PathVariable Integer id) {
        if (!ratingRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        ratingRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
