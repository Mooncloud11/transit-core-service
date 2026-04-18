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
 * PredictionService: Java Backend ile Python AI Engine arasındaki köprü.
 * 
 * İki ana görev:
 * 1. getLinePrediction() → Hat bazlı gecikme tahmini (AI veya Fallback)
 * 2. getNextBuses() → Durak için sıradaki otobüs tahmini (AI veya Fallback)
 * 
 * Fallback Mekanizması:
 * - Python AI Engine çöktüğünde veya Gemini 429 quota hatası verdiğinde
 * Frontend'in beklediği formatta yedek veri döndürür.
 */
@Slf4j
@Service
public class PredictionService {
    private final PredictionResponseNormalizer responseNormalizer;
    private final RestClient restClient;

    // Python AI Engine adresi (application.properties → python.api.url)
    @Value("${python.api.url:http://localhost:8000}")
    private String PYTHON_AI_URL;

    public PredictionService(PredictionResponseNormalizer responseNormalizer) {
        this.responseNormalizer = responseNormalizer;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Hat bazlı gecikme tahmini.
     * Python AI Engine'in /predict endpoint'ine istek atar.
     * Hata durumunda Frontend'in beklediği formatta fallback döner.
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
                return responseNormalizer.normalizePredictionResponse(result, lineCode);
            }
        } catch (HttpClientErrorException e) {
           log.warn("Python AI HTTP error: {} - {}", e.getStatusCode(), e.getMessage());
        } catch (ResourceAccessException e) {
            log.warn("Could not connect to Python AI engine: {}", e.getMessage());
        } catch (Exception e) {
           log.error("Unexpected error: {}", e.getMessage(), e);
        }

        // ═══ FALLBACK: Frontend'in beklediği formatta yedek veri ═══
        return generateFallbackPrediction(lineCode);
    }

    /**
     * Sıradaki otobüsler tahmini.
     * Python AI Engine'in /next-buses endpoint'ine istek atar.
     */
    @Cacheable(value = "nextBuses", key = "@predictionService.buildNextBusesCacheKey(#lineCode, #stopId, #reqHour, #reqMinute)", condition = "#reqHour != null && #reqMinute != null")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNextBuses(String lineCode, String stopId, Integer reqHour, Integer reqMinute) {
        validateTimeParameters(reqHour, reqMinute);
        LocalTime now = LocalTime.now();
        int hour = (reqHour != null) ? reqHour : now.getHour();
        int minute = (reqMinute != null) ? reqMinute : now.getMinute();

        String url = String.format("%s/next-buses?line_code=%s&stop_id=%s&hour=%d&minute=%d",
                PYTHON_AI_URL, lineCode, stopId, hour, minute);

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
                        () -> generateFallbackNextBuses(lineCode, stopId));
            }
        } catch (HttpClientErrorException e) {
            log.warn("Next buses HTTP error: {}", e.getStatusCode());
        } catch (ResourceAccessException e) {
            log.warn("Could not connect to Python AI engine (next-buses): {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while fetching next buses: {}", e.getMessage(), e);
        }

        // ═══ FALLBACK: Sıradaki otobüsler yedek verisi ═══
        return generateFallbackNextBuses(lineCode, stopId);
    }

    /**
     * Gecikme tahmini için fallback veri.
     * Frontend'in beklediği tam formatta: real_time_delay_min, status_color,
     * passenger_advice
     */
    private Map<String, Object> generateFallbackPrediction(String lineCode) {
        Map<String, Object> fallback = new HashMap<>();

        // Hat bazlı ortalama gecikmeler (CSV'den türetilmiş sabit değerler)
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

        Map<String, String> routeDetails = new HashMap<>();
        routeDetails.put("line", lineCode);
        routeDetails.put("monthly_occupancy", "N/A");
        routeDetails.put("crowding_status", "normal");
        fallback.put("route_details", routeDetails);

        return fallback;
    }

    /**
     * Sıradaki otobüsler için fallback veri.
     */
    private Map<String, Object> generateFallbackNextBuses(String lineCode, String stopId) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("line_id", lineCode);
        fallback.put("stop_id", stopId);
        fallback.put("is_fallback", true);
        fallback.put("weather", "clear");
        fallback.put("traffic_level", "normal");

        // 3 sıradaki otobüs için varsayılan süreler
        List<Map<String, Object>> buses = List.of(
                Map.of("bus_order", 1, "estimated_arrival_min", 8.0, "crowding_forecast", "normal", "confidence", 0.7),
                Map.of("bus_order", 2, "estimated_arrival_min", 22.0, "crowding_forecast", "quiet", "confidence", 0.5),
                Map.of("bus_order", 3, "estimated_arrival_min", 35.0, "crowding_forecast", "quiet", "confidence", 0.3));
        fallback.put("next_buses", buses);

        return fallback;
    }

    public String buildPredictionCacheKey(String lineCode, Integer reqHour, Integer reqMinute) {
        int minuteBucket = reqMinute / 5;
        return lineCode + "-" + reqHour + "-" + minuteBucket;
    }

    public String buildNextBusesCacheKey(String lineCode, String stopId, Integer reqHour, Integer reqMinute) {
        int minuteBucket = reqMinute / 5;
        return lineCode + "-" + stopId + "-" + reqHour + "-" + minuteBucket;
    }

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