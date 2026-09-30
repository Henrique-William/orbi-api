package com.tech.orbi.service;

import com.tech.orbi.Repository.RouteRepository;
import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteDirectionsDto;
import com.tech.orbi.dto.RouteDirectionsDto.ExtraSummary;
import com.tech.orbi.dto.RouteDirectionsDto.Point;
import com.tech.orbi.entity.Delivery;
import com.tech.orbi.entity.Route;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RouteService {

    private final OpenRouteServiceClient orsClient;
    private final RouteRepository routeRepository;
    private final boolean surfaceWeightEnabled;

    public RouteService(
            OpenRouteServiceClient orsClient,
            RouteRepository routeRepository,
            @Value("${orbi.route.surface-weight-enabled:true}") boolean surfaceWeightEnabled) {
        this.orsClient = orsClient;
        this.routeRepository = routeRepository;
        this.surfaceWeightEnabled = surfaceWeightEnabled;
    }

    public List<LocationDto> findBestRoute(List<LocationDto> locations, int startIndex) {
        validateInput(locations, startIndex);
        if (locations.size() == 1) {
            return locations;
        }

        double[][] distances = orsClient.getDistanceMatrix(locations);
        validateMatrix(distances, locations.size());

        List<LocationDto> optimizedRoute = new ArrayList<>();
        boolean[] visited = new boolean[locations.size()];
        int currentIndex = startIndex;

        for (int i = 0; i < locations.size(); i++) {
            optimizedRoute.add(locations.get(currentIndex));
            visited[currentIndex] = true;

            int nextIndex = selectNextCandidate(locations, distances, visited, currentIndex);
            if (nextIndex != -1) {
                currentIndex = nextIndex;
            }
        }

        optimizedRoute.add(locations.get(startIndex));
        return optimizedRoute;
    }

    @Transactional(readOnly = true)
    public RouteDirectionsDto getDirections(Integer routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada"));

        List<Delivery> deliveries = route.getDeliveries();
        if (deliveries == null || deliveries.size() < 2) {
            throw new IllegalStateException("A rota deve possuir pelo menos duas entregas para calcular as direções.");
        }

        List<Point> points = deliveries.stream()
                .sorted(Comparator.comparingInt(Delivery::getOrder))
                .map(this::toPoint)
                .toList();
        RouteDirectionsDto directions = orsClient.getDirections(points);

        return new RouteDirectionsDto(
                routeId,
                directions.distanceMeters(),
                directions.durationSeconds(),
                directions.geometry(),
                directions.segments(),
                directions.extras()
        );
    }

    private int selectNextCandidate(
            List<LocationDto> locations,
            double[][] distances,
            boolean[] visited,
            int currentIndex) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < locations.size(); i++) {
            if (!visited[i]) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator
                .comparingDouble((Integer candidate) -> distances[currentIndex][candidate])
                .thenComparingInt(Integer::intValue));

        double bestCost = Double.POSITIVE_INFINITY;
        int bestCandidate = -1;

        for (int candidate : candidates) {
            double distance = distances[currentIndex][candidate];
            if (bestCandidate != -1 && distance >= bestCost) {
                break;
            }

            double roadFactor = surfaceWeightEnabled
                    ? getRoadFactor(locations.get(currentIndex), locations.get(candidate))
                    : 1.0;
            double cost = distance * roadFactor;
            if (cost < bestCost) {
                bestCost = cost;
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private double getRoadFactor(LocationDto origin, LocationDto destination) {
        RouteDirectionsDto directions = orsClient.getDirections(List.of(
                new Point(origin.latitude(), origin.longitude()),
                new Point(destination.latitude(), destination.longitude())
        ));
        var surface = directions.extras().get("surface");
        return calculateSurfaceFactor(surface == null ? null : surface.summary());
    }

    private double calculateSurfaceFactor(List<ExtraSummary> summary) {
        if (summary == null || summary.isEmpty()) {
            return 1.0;
        }

        double weightedFactors = 0.0;
        double totalDistance = 0.0;
        for (ExtraSummary item : summary) {
            if (item == null || !Double.isFinite(item.distanceMeters()) || item.distanceMeters() < 0) {
                throw new IllegalArgumentException("O resumo de superfície contém uma distância inválida.");
            }
            weightedFactors += item.distanceMeters() * factorFor(item.value());
            totalDistance += item.distanceMeters();
        }
        return totalDistance == 0.0 ? 1.0 : Math.max(1.0, weightedFactors / totalDistance);
    }

    private double factorFor(int surface) {
        return switch (surface) {
            case 1 -> 1.02;
            case 2 -> 1.20;
            case 3, 4 -> 1.00;
            case 5 -> 1.10;
            case 6 -> 1.05;
            case 7 -> 1.10;
            case 8, 9 -> 1.12;
            case 10 -> 1.15;
            case 11 -> 1.25;
            case 12 -> 1.20;
            case 13 -> 1.30;
            case 14 -> 1.08;
            case 15 -> 1.30;
            case 16 -> 1.20;
            case 17 -> 1.25;
            case 18 -> 1.15;
            default -> 1.00;
        };
    }

    private Point toPoint(Delivery delivery) {
        if (delivery == null) {
            throw new IllegalStateException("A rota contém uma entrega nula.");
        }
        BigDecimal latitude = delivery.getDropoffLatitude();
        BigDecimal longitude = delivery.getDropoffLongitude();
        if (latitude == null || longitude == null) {
            throw new IllegalStateException("A rota contém uma entrega sem coordenadas.");
        }

        Point point = new Point(latitude.doubleValue(), longitude.doubleValue());
        validateCoordinate(point.latitude(), point.longitude(), delivery.getDropoffAddress());
        return point;
    }

    private void validateInput(List<LocationDto> locations, int startIndex) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("A lista de localizações não pode ser nula ou vazia.");
        }
        if (startIndex < 0 || startIndex >= locations.size()) {
            throw new IllegalArgumentException("O índice inicial deve apontar para uma localização existente.");
        }
        if (locations.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("A lista de localizações não pode conter elementos nulos.");
        }
        for (LocationDto location : locations) {
            validateCoordinate(location.latitude(), location.longitude(), location.address());
        }
    }

    private void validateCoordinate(double latitude, double longitude, String address) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude inválida para a localização '" + address + "'.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude inválida para a localização '" + address + "'.");
        }
    }

    private void validateMatrix(double[][] matrix, int expectedSize) {
        if (matrix == null || matrix.length != expectedSize) {
            throw new IllegalStateException("A matriz de distâncias possui dimensões incompatíveis.");
        }
        for (double[] row : matrix) {
            if (row == null || row.length != expectedSize) {
                throw new IllegalStateException("A matriz de distâncias possui dimensões incompatíveis.");
            }
            for (double distance : row) {
                if (!Double.isFinite(distance) || distance < 0) {
                    throw new IllegalStateException("A matriz de distâncias contém um valor inválido.");
                }
            }
        }
    }
}
