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

import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        String url = String.format("%s/predict?line_code=%s&hour=%d&minute=%d",
                PYTHON_AI_URL, lineCode, hour, minute);

        try {
            Map result = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            if (result != null) {
                return result;
            }
        } catch (HttpClientErrorException e) {
            log.warn("Python AI HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.warn("Could not connect to Python AI engine: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
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

        StringBuilder urlBuilder = new StringBuilder(String.format(
                "%s/next-buses?line_code=%s&stop_id=%s&hour=%d&minute=%d",
                PYTHON_AI_URL, lineCode, stopId, hour, minute));

        if (destinationId != null && !destinationId.isBlank()) {
            urlBuilder.append("&destination_id=").append(destinationId);
        }

        String url = urlBuilder.toString();

        try {
            Map result = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            if (result != null) {
                return responseNormalizer.normalizeNextBusesResponse(
                        result,
                        lineCode,
                        stopId,
                        () -> generateFallbackNextBuses(lineCode, stopId, destinationId));
            }
        } catch (HttpClientErrorException e) {
            log.warn("Next buses HTTP error: {}", e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Could not connect to Python AI engine (next-buses): {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while fetching next buses: {}", e.getMessage(), e);
        }

        return generateFallbackNextBuses(lineCode, stopId, destinationId);
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
