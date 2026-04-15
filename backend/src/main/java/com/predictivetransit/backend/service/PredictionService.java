package com.predictivetransit.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
 *   Frontend'in beklediği formatta yedek veri döndürür.
 */
@Service
public class PredictionService {

    private final RestTemplate restTemplate;
    
    // Python AI Engine adresi (Environment variable destekli)
    private final String PYTHON_AI_URL = System.getenv("AI_ENGINE_URL") != null ? System.getenv("AI_ENGINE_URL") : "http://localhost:8000";

    public PredictionService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Hat bazlı gecikme tahmini.
     * Python AI Engine'in /predict endpoint'ine istek atar.
     * Hata durumunda Frontend'in beklediği formatta fallback döner.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getLinePrediction(String lineCode, Integer reqHour, Integer reqMinute) {
        LocalTime now = LocalTime.now();
        int hour = (reqHour != null) ? reqHour : now.getHour();
        int minute = (reqMinute != null) ? reqMinute : now.getMinute();

        String url = String.format("%s/predict?line_code=%s&hour=%d&minute=%d",
                PYTHON_AI_URL, lineCode, hour, minute);

        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null) {
                return result;
            }
        } catch (HttpClientErrorException e) {
            System.out.println("[UYARI] Python AI HTTP Hatası: " + e.getStatusCode() + " - " + e.getMessage());
        } catch (ResourceAccessException e) {
            System.out.println("[UYARI] Python AI Engine'e bağlanılamadı: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[HATA] Beklenmeyen hata: " + e.getMessage());
        }

        // ═══ FALLBACK: Frontend'in beklediği formatta yedek veri ═══
        return generateFallbackPrediction(lineCode);
    }

    /**
     * Sıradaki otobüsler tahmini.
     * Python AI Engine'in /next-buses endpoint'ine istek atar.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNextBuses(String lineCode, String stopId, Integer reqHour, Integer reqMinute) {
        LocalTime now = LocalTime.now();
        int hour = (reqHour != null) ? reqHour : now.getHour();
        int minute = (reqMinute != null) ? reqMinute : now.getMinute();

        String url = String.format("%s/next-buses?line_code=%s&stop_id=%s&hour=%d&minute=%d",
                PYTHON_AI_URL, lineCode, stopId, hour, minute);

        try {
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            if (result != null) {
                return result;
            }
        } catch (HttpClientErrorException e) {
            System.out.println("[UYARI] Sıradaki otobüs HTTP Hatası: " + e.getStatusCode());
        } catch (ResourceAccessException e) {
            System.out.println("[UYARI] Python AI Engine'e bağlanılamadı (next-buses): " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[HATA] Sıradaki otobüs beklenmeyen hata: " + e.getMessage());
        }

        // ═══ FALLBACK: Sıradaki otobüsler yedek verisi ═══
        return generateFallbackNextBuses(lineCode, stopId);
    }

    /**
     * Gecikme tahmini için fallback veri.
     * Frontend'in beklediği tam formatta: real_time_delay_min, status_color, passenger_advice
     */
    private Map<String, Object> generateFallbackPrediction(String lineCode) {
        Map<String, Object> fallback = new HashMap<>();
        
        // Hat bazlı ortalama gecikmeler (CSV'den türetilmiş sabit değerler)
        Map<String, Double> avgDelays = Map.of(
            "L01", 4.5, "L02", 5.2, "L03", 3.8, "L04", 6.1, "L05", 4.0
        );
        
        double delay = avgDelays.getOrDefault(lineCode, 5.0);
        String color;
        if (delay > 6) color = "YELLOW";
        else if (delay > 8) color = "RED";
        else color = "GREEN";

        fallback.put("real_time_delay_min", delay);
        fallback.put("status_color", color);
        fallback.put("passenger_advice", "AI servisi şu an yanıt veremiyor. Tahmini süreler geçmiş ortalamalara dayanmaktadır.");
        fallback.put("is_fallback", true);

        Map<String, String> routeDetails = new HashMap<>();
        routeDetails.put("line", lineCode);
        routeDetails.put("monthly_occupancy", "N/A");
        routeDetails.put("crowding_status", "Normal");
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
            Map.of("bus_order", 3, "estimated_arrival_min", 35.0, "crowding_forecast", "quiet", "confidence", 0.3)
        );
        fallback.put("next_buses", buses);

        return fallback;
    }
}