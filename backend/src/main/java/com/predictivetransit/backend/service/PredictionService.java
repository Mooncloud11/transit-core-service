package com.predictivetransit.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PredictionService: The bridge between the Java Backend and the Python AI
 * Engine.
 * 
 * Two main responsibilities:
 * 1. getLinePrediction() → Line-based delay forecasting (AI-driven or Fallback)
 * 2. getNextBuses() → Stop-specific next bus estimation (AI-driven or Fallback)
 * 
 * Fallback Mechanism:
 * - Triggered if the Python AI Engine is unreachable or if the AI API (e.g.,
 * Gemini)
 * returns a 429 quota error. Returns historical averages to keep the Frontend
 * functional.
 */
@Slf4j
@Service
public class PredictionService {
    private static final int MAX_AI_ATTEMPTS = 2;

    private final PredictionResponseNormalizer responseNormalizer;
    private final RestClient restClient;

    // Python AI Engine base URL (configured via application.properties →
    // python.api.url)
    @Value("${python.api.url:http://localhost:8000}")
    private String PYTHON_AI_URL;

    public PredictionService(PredictionResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;

        // Configure short timeouts to ensure the backend remains responsive even if AI
        // engine is slow
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Line-based delay prediction.
     * Hits the /predict endpoint of the Python AI Engine.
     * Automatically returns fallback data if any error occurs.
     */
    @Cacheable(value = "predictions", key = "@predictionService.buildPredictionCacheKey(#lineCode, #reqHour, #reqMinute)", condition = "#reqHour != null && #reqMinute != null")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLinePrediction(String lineCode, Integer reqHour, Integer reqMinute) {
        validateTimeParameters(reqHour, reqMinute);
        LocalTime now = LocalTime.now();
        int hour = (reqHour != null) ? reqHour : now.getHour();
        int minute = (reqMinute != null) ? reqMinute : now.getMinute();

        Map<String, Object> result = callAiWithRetry(uriBuilder -> uriBuilder
                .path("/predict")
                .queryParam("line_code", lineCode)
                .queryParam("hour", hour)
                .queryParam("minute", minute)
                .build());

        if (result != null) {
            return result;
        }

        // ═══ FALLBACK: Provide historical average data if AI is unavailable ═══
        return generateFallbackPrediction(lineCode);
    }

    /**
     * Next buses prediction for a specific stop.
     * Hits the /next-buses endpoint of the Python AI Engine.
     */
    @Cacheable(value = "nextBuses", key = "@predictionService.buildNextBusesCacheKey(#lineCode, #stopId, #destinationId, #reqHour, #reqMinute)", condition = "#reqHour != null && #reqMinute != null")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNextBuses(String lineCode, String stopId, String destinationId, Integer reqHour,
            Integer reqMinute) {
        validateTimeParameters(reqHour, reqMinute);
        LocalTime now = LocalTime.now();
        int hour = (reqHour != null) ? reqHour : now.getHour();
        int minute = (reqMinute != null) ? reqMinute : now.getMinute();

        Map<String, Object> result = callAiWithRetry(uriBuilder -> {
            UriBuilder builder = uriBuilder
                    .path("/next-buses")
                    .queryParam("line_code", lineCode)
                    .queryParam("stop_id", stopId)
                    .queryParam("hour", hour)
                    .queryParam("minute", minute);

            if (destinationId != null && !destinationId.isBlank()) {
                builder.queryParam("destination_id", destinationId);
            }

            return builder.build();
        });

        if (result != null) {
            return responseNormalizer.normalizeNextBusesResponse(
                    result,
                    lineCode,
                    stopId,
                    () -> generateFallbackNextBuses(lineCode, stopId, destinationId));
        }

        return generateFallbackNextBuses(lineCode, stopId, destinationId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiWithRetry(Function<UriBuilder, URI> uriFactory) {
        List<String> errors = new ArrayList<>();

        for (int attempt = 1; attempt <= MAX_AI_ATTEMPTS; attempt++) {
            try {
                Map result = restClient.get()
                        .uri(uriBuilder -> {
                            UriBuilder baseBuilder = uriBuilder
                                    .scheme("http")
                                    .host(extractHost(PYTHON_AI_URL))
                                    .port(extractPort(PYTHON_AI_URL));
                            return uriFactory.apply(baseBuilder);
                        })
                        .retrieve()
                        .body(Map.class);

                if (result != null) {
                    return result;
                }
            } catch (HttpClientErrorException e) {
                errors.add("HTTP " + e.getStatusCode().value());
                if (e.getStatusCode().is4xxClientError() && e.getStatusCode().value() != 429) {
                    break;
                }
            } catch (ResourceAccessException e) {
                errors.add("Resource access: " + e.getMessage());
            } catch (Exception e) {
                errors.add("Unexpected: " + e.getMessage());
            }

            if (attempt < MAX_AI_ATTEMPTS) {
                try {
                    Thread.sleep(150L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!errors.isEmpty()) {
            log.warn("AI call failed after {} attempt(s): {}", MAX_AI_ATTEMPTS, String.join(" | ", errors));
        }
        return null;
    }

    private String extractHost(String baseUrl) {
        String sanitized = baseUrl.replace("http://", "").replace("https://", "");
        int slashIndex = sanitized.indexOf('/');
        if (slashIndex >= 0) {
            sanitized = sanitized.substring(0, slashIndex);
        }

        int colonIndex = sanitized.indexOf(':');
        return colonIndex >= 0 ? sanitized.substring(0, colonIndex) : sanitized;
    }

    private int extractPort(String baseUrl) {
        String sanitized = baseUrl.replace("http://", "").replace("https://", "");
        int slashIndex = sanitized.indexOf('/');
        if (slashIndex >= 0) {
            sanitized = sanitized.substring(0, slashIndex);
        }

        int colonIndex = sanitized.indexOf(':');
        if (colonIndex >= 0) {
            String portText = sanitized.substring(colonIndex + 1);
            try {
                return Integer.parseInt(portText);
            } catch (NumberFormatException ignored) {
                return 8000;
            }
        }
        return 8000;
    }

    /**
     * Generates fallback data for delay prediction.
     * Matches the format expected by the frontend: real_time_delay_min,
     * status_color, passenger_advice.
     */
    private Map<String, Object> generateFallbackPrediction(String lineCode) {
        Map<String, Object> fallback = new HashMap<>();

        // Static average delays derived from historical CSV analysis
        Map<String, Double> avgDelays = Map.of(
                "L01", 4.5, "L02", 5.2, "L03", 3.8, "L04", 6.1, "L05", 4.0);

        double delay = avgDelays.getOrDefault(lineCode, 5.0);
        String color;
        if (delay > 8)
            color = "RED";
        else if (delay > 6)
            color = "YELLOW";
        else
            color = "GREEN";

        fallback.put("real_time_delay_min", delay);
        fallback.put("status_color", color);
        fallback.put("passenger_advice",
                "AI service is currently unavailable. Estimated times are based on historical averages.");
        fallback.put("is_fallback", true);

        // Keep fallback schema aligned with Python /predict response expected by
        // frontend.
        Map<String, Integer> lineStopCounts = Map.of(
                "L01", 14,
                "L02", 11,
                "L03", 9,
                "L04", 12,
                "L05", 16);
        int numStops = lineStopCounts.getOrDefault(lineCode, 10);
        int currentBusStopIndex = Math.min((int) delay % Math.max(1, numStops), numStops - 1);

        List<Double> stopEtas = new java.util.ArrayList<>();
        for (int i = 0; i < numStops; i++) {
            if (i <= currentBusStopIndex) {
                stopEtas.add(0.0);
            } else {
                stopEtas.add(roundToOneDecimal(delay + (i - currentBusStopIndex) * 3.5));
            }
        }

        fallback.put("line_code", lineCode);
        fallback.put("current_bus_stop_index", currentBusStopIndex);
        fallback.put("stop_etas", stopEtas);

        return fallback;
    }

    private double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * Generates fallback data for the next 3 buses at a stop.
     */
    private Map<String, Object> generateFallbackNextBuses(String lineCode, String stopId, String destinationId) {
    Map<String, Object> fallback = new HashMap<>();
    fallback.put("line_id", lineCode);
    fallback.put("stop_id", stopId);

    if (destinationId != null && !destinationId.isBlank()) {
        fallback.put("destination_id", destinationId);
    }

    fallback.put("is_fallback", true);
    fallback.put("weather", "clear");
    fallback.put("traffic_level", "normal");

    List<Map<String, Object>> buses = List.of(
            Map.of("bus_order", 1, "estimated_arrival_min", 8.0, "crowding_forecast", "normal", "confidence", 0.7),
            Map.of("bus_order", 2, "estimated_arrival_min", 22.0, "crowding_forecast", "quiet", "confidence", 0.5),
            Map.of("bus_order", 3, "estimated_arrival_min", 35.0, "crowding_forecast", "quiet", "confidence", 0.3));

    fallback.put("next_buses", buses);
    return fallback;
}
    /**
     * Cache Key Generators for Spring Cache
     */
    public String buildPredictionCacheKey(String lineCode, Integer reqHour, Integer reqMinute) {
        int minuteBucket = reqMinute / 5; // Group by 5-minute windows
        return lineCode + "-" + reqHour + "-" + minuteBucket;
    }

    public String buildNextBusesCacheKey(String lineCode, String stopId, String destinationId, Integer reqHour, Integer reqMinute) {
    int minuteBucket = reqMinute / 5;
    String normalizedDestinationId = (destinationId == null || destinationId.isBlank())
            ? "no-destination"
            : destinationId;

    return lineCode + "-" + stopId + "-" + normalizedDestinationId + "-" + reqHour + "-" + minuteBucket;
}

    /**
     * Validates that both hour and minute are either present or absent together.
     */
    private void validateTimeParameters(Integer reqHour, Integer reqMinute) {
        boolean hourProvided = reqHour != null;
        boolean minuteProvided = reqMinute != null;

        if (hourProvided != minuteProvided) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "hour and minute must be provided together or both omitted");
        }

        if (reqHour != null && (reqHour < 0 || reqHour > 23)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "hour must be between 0 and 23");
        }

        if (reqMinute != null && (reqMinute < 0 || reqMinute > 59)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minute must be between 0 and 59");
        }
    }
}
