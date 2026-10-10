package com.tech.orbi.service;

import com.tech.orbi.dto.LocationDto;
import com.tech.orbi.dto.RouteDirectionsDto;
import com.tech.orbi.dto.RouteDirectionsDto.ExtraInfo;
import com.tech.orbi.dto.RouteDirectionsDto.ExtraSummary;
import com.tech.orbi.dto.RouteDirectionsDto.Geometry;
import com.tech.orbi.dto.RouteDirectionsDto.Point;
import com.tech.orbi.dto.RouteDirectionsDto.Segment;
import com.tech.orbi.dto.RouteDirectionsDto.Step;
import com.tech.orbi.dto.ors.OrsDtos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouteServiceClient {

    private static final String MATRIX_PATH = "/v2/matrix/driving-car";
    private static final String DIRECTIONS_PATH = "/v2/directions/driving-car/geojson";
    private static final List<String> EXTRA_INFO = List.of(
            "surface", "waytype", "waycategory", "tollways"
    );

    private final RestClient restClient;
    private final String apiKey;

    public OpenRouteServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${ors.base-url}") String baseUrl,
            @Value("${ors.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public double[][] getDistanceMatrix(List<LocationDto> locations) {
        if (locations == null || locations.isEmpty() || locations.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("A lista de localizações não pode ser nula, vazia ou conter elementos nulos.");
        }
        requireApiKey();

        var coordinates = locations.stream()
                .map(location -> List.of(location.longitude(), location.latitude()))
                .toList();
        var request = new OrsDtos.MatrixRequest(coordinates, List.of("distance"), "km", false);

        try {
            var response = restClient.post()
                    .uri(MATRIX_PATH)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .body(request)
                    .retrieve()
                    .body(OrsDtos.MatrixResponse.class);
            return toValidatedMatrix(response, locations.size());
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "O OpenRouteService não conseguiu calcular a matriz de distâncias (HTTP "
                            + exception.getStatusCode().value() + ").",
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Não foi possível comunicar com o OpenRouteService para calcular a matriz de distâncias.",
                    exception
            );
        }
    }

    public RouteDirectionsDto getDirections(List<Point> points) {
        validatePoints(points);
        requireApiKey();

        var coordinates = points.stream()
                .map(point -> List.of(point.longitude(), point.latitude()))
                .toList();
        var request = new OrsDtos.DirectionsRequest(coordinates, true, "m", EXTRA_INFO);

        try {
            var response = restClient.post()
                    .uri(DIRECTIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .body(request)
                    .retrieve()
                    .body(OrsDtos.DirectionsResponse.class);
            return toDirectionsDto(response);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "O OpenRouteService não conseguiu calcular as direções da rota (HTTP "
                            + exception.getStatusCode().value() + ").",
                    exception
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Não foi possível comunicar com o OpenRouteService para calcular as direções da rota.",
                    exception
            );
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "A chave do OpenRouteService não está configurada. Defina a variável de ambiente ORS_API_KEY.");
        }
    }

    private void validatePoints(List<Point> points) {
        if (points == null || points.size() < 2 || points.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("São necessários pelo menos dois pontos válidos para calcular as direções.");
        }
        for (Point point : points) {
            if (!Double.isFinite(point.latitude()) || point.latitude() < -90 || point.latitude() > 90
                    || !Double.isFinite(point.longitude()) || point.longitude() < -180 || point.longitude() > 180) {
                throw new IllegalArgumentException("A lista de pontos contém uma coordenada inválida.");
            }
        }
    }

    private double[][] toValidatedMatrix(OrsDtos.MatrixResponse response, int expectedSize) {
        if (response == null || response.distances() == null || response.distances().length != expectedSize) {
            throw invalidMatrixException();
        }

        double[][] matrix = new double[expectedSize][expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            Double[] sourceRow = response.distances()[i];
            if (sourceRow == null || sourceRow.length != expectedSize) {
                throw invalidMatrixException();
            }
            for (int j = 0; j < expectedSize; j++) {
                Double distance = sourceRow[j];
                if (distance == null || !Double.isFinite(distance) || distance < 0) {
                    throw invalidMatrixException();
                }
                matrix[i][j] = distance;
            }
        }
        return matrix;
    }

    private RouteDirectionsDto toDirectionsDto(OrsDtos.DirectionsResponse response) {
        if (response == null || !"FeatureCollection".equals(response.type())
                || response.features() == null || response.features().isEmpty()) {
            throw invalidDirectionsException();
        }

        var feature = response.features().getFirst();
        if (feature == null || feature.geometry() == null || feature.properties() == null) {
            throw invalidDirectionsException();
        }

        var geometry = feature.geometry();
        var properties = feature.properties();
        if (!"LineString".equals(geometry.type()) || geometry.coordinates() == null
                || geometry.coordinates().isEmpty()
                || geometry.coordinates().stream().anyMatch(this::isInvalidCoordinate)
                || properties.summary() == null || properties.segments() == null) {
            throw invalidDirectionsException();
        }

        List<Segment> segments = properties.segments().stream().map(this::toSegment).toList();
        return new RouteDirectionsDto(
                null,
                requireNonNegative(properties.summary().distance()),
                requireNonNegative(properties.summary().duration()),
                new Geometry(geometry.type(), geometry.coordinates()),
                segments,
                toExtras(properties.extras())
        );
    }

    private Segment toSegment(OrsDtos.Segment segment) {
        if (segment == null || segment.steps() == null) {
            throw invalidDirectionsException();
        }
        return new Segment(
                requireNonNegative(segment.distance()),
                requireNonNegative(segment.duration()),
                segment.steps().stream().map(this::toStep).toList()
        );
    }

    private Step toStep(OrsDtos.Step step) {
        if (step == null || step.instruction() == null || step.type() == null || step.wayPoints() == null) {
            throw invalidDirectionsException();
        }
        return new Step(
                step.name(),
                step.instruction(),
                requireNonNegative(step.distance()),
                requireNonNegative(step.duration()),
                step.type(),
                step.wayPoints()
        );
    }

    private Map<String, ExtraInfo> toExtras(Map<String, OrsDtos.ExtraInfo> orsExtras) {
        Map<String, ExtraInfo> extras = new LinkedHashMap<>();
        if (orsExtras == null) {
            return extras;
        }

        for (String name : EXTRA_INFO) {
            OrsDtos.ExtraInfo extra = orsExtras.get(name);
            if (extra == null) {
                continue;
            }
            List<List<Integer>> values = extra.values() == null
                    ? List.of()
                    : extra.values().stream().map(this::validateExtraValue).toList();
            List<ExtraSummary> summary = extra.summary() == null
                    ? List.of()
                    : extra.summary().stream().map(this::toExtraSummary).toList();
            extras.put(name, new ExtraInfo(values, summary));
        }
        return extras;
    }

    private List<Integer> validateExtraValue(List<Integer> value) {
        if (value == null || value.size() != 3 || value.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidDirectionsException();
        }
        return value;
    }

    private ExtraSummary toExtraSummary(OrsDtos.ExtraSummary summary) {
        if (summary == null || summary.value() == null) {
            throw invalidDirectionsException();
        }
        return new ExtraSummary(
                summary.value(),
                requireNonNegative(summary.distance()),
                requireNonNegative(summary.amount())
        );
    }

    private boolean isInvalidCoordinate(List<Double> coordinate) {
        return coordinate == null || coordinate.size() < 2
                || coordinate.get(0) == null || coordinate.get(1) == null
                || !Double.isFinite(coordinate.get(0)) || !Double.isFinite(coordinate.get(1));
    }

    private double requireNonNegative(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw invalidDirectionsException();
        }
        return value;
    }

    private IllegalStateException invalidMatrixException() {
        return new IllegalStateException(
                "O OpenRouteService retornou uma matriz de distâncias inválida ou incompatível com as localizações.");
    }

    private IllegalStateException invalidDirectionsException() {
        return new IllegalStateException(
                "O OpenRouteService retornou direções inválidas ou incompletas para a rota.");
    }
}
