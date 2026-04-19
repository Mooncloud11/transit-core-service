package com.predictivetransit.backend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * PredictionResponseNormalizer: Ensures that raw responses from the Python AI Engine
 * are transformed into a consistent, stable format expected by the frontend.
 * It handles data type conversions and applies default values when the AI response is incomplete.
 */
@Component
public class PredictionResponseNormalizer {

    /**
     * Normalizes the "next buses" response.
     * @param raw Raw Map from the AI Engine response.
     * @param lineCode The bus line code.
     * @param stopId The bus stop ID.
    * @param fallbackSupplier A supplier that provides fallback data if normalization fails.
     * @return A sanitized Map with normalized bus arrival details.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizeNextBusesResponse(
            Map raw,
            String lineCode,
            String stopId,
            Supplier<Map<String, Object>> fallbackSupplier) {

        Map<String, Object> normalized = new HashMap<>();

        normalized.put("line_id", safeString(raw.get("line_id"), lineCode));
        normalized.put("stop_id", safeString(raw.get("stop_id"), stopId));
        normalized.put("weather", safeString(raw.get("weather"), "clear"));
        normalized.put("traffic_level", safeString(raw.get("traffic_level"), "normal"));
        normalized.put("is_fallback", safeBoolean(raw.get("is_fallback"), false));

        List<Map<String, Object>> normalizedBuses = new ArrayList<>();

        Object busesObj = raw.get("next_buses");
        if (busesObj instanceof List<?> rawList) {
            int fallbackOrder = 1;

            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> rawBus)) {
                    continue;
                }

                Map<String, Object> bus = new HashMap<>();
                bus.put("bus_order", safeInt(rawBus.get("bus_order"), fallbackOrder));
                bus.put("planned_arrival_min", safeDouble(rawBus.get("planned_arrival_min"), fallbackOrder * 15.0));
                bus.put("estimated_arrival_min", safeDouble(rawBus.get("estimated_arrival_min"), fallbackOrder * 15.0));

                if (fallbackOrder == 1) {
                    bus.put("current_bus_location_index", safeInt(rawBus.get("current_bus_location_index"), 0));
                }

                if (rawBus.containsKey("crowding_forecast")) {
                    bus.put("crowding_forecast", normalizeCrowdingForecast(rawBus.get("crowding_forecast")));
                    bus.put("confidence", normalizeConfidence(rawBus.get("confidence")));
                } else {
                    bus.put("crowding_forecast", "normal");
                    bus.put("confidence", 0.5);
                }

                normalizedBuses.add(bus);
                fallbackOrder++;
            }
        }

        if (normalizedBuses.isEmpty()) {
            return fallbackSupplier.get();
        }

        normalized.put("next_buses", normalizedBuses);
        return normalized;
    }

    private String normalizeCrowdingForecast(Object value) {
        String forecast = safeString(value, "normal").trim().toLowerCase();

        if (forecast.equals("busy") || forecast.equals("normal") || forecast.equals("quiet")) {
            return forecast;
        }

        if (forecast.equals("ai offline")) {
            return "normal";
        }
        if (forecast.equals("crowded")) {
            return "busy";
        }
        if (forecast.equals("low")) {
            return "quiet";
        }

        return "normal";
    }

    private double normalizeConfidence(Object value) {
        double confidence = safeDouble(value, 0.5);

        if (confidence < 0.0) {
            return 0.0;
        }
        if (confidence > 1.0) {
            return 1.0;
        }
        return confidence;
    }

    private String safeString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        String result = String.valueOf(value).trim();
        return result.isEmpty() ? defaultValue : result;
    }

    private double safeDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private int safeInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean safeBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        String normalized = String.valueOf(value).trim().toLowerCase();
        if (normalized.equals("true")) {
            return true;
        }
        if (normalized.equals("false")) {
            return false;
        }

        return defaultValue;
    }
}