package com.predictivetransit.backend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class PredictionResponseNormalizer {

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalizePredictionResponse(Map raw, String lineCode) {
        Map<String, Object> normalized = new HashMap<>();

        double delay = safeDouble(raw.get("real_time_delay_min"), 5.0);
        String statusColor = normalizeStatusColor(raw.get("status_color"), delay);
        String passengerAdvice = safeString(
                raw.get("passenger_advice"),
                "Prediction generated with partial backend normalization.");

        Map<String, Object> rawRouteDetails =
                (raw.get("route_details") instanceof Map)
                        ? (Map<String, Object>) raw.get("route_details")
                        : new HashMap<>();

        Map<String, Object> routeDetails = new HashMap<>();
        routeDetails.put("line", safeString(rawRouteDetails.get("line"), lineCode));
        routeDetails.put("monthly_occupancy", safeString(rawRouteDetails.get("monthly_occupancy"), "N/A"));
        routeDetails.put("crowding_status", normalizeCrowdingStatus(rawRouteDetails.get("crowding_status")));

        normalized.put("real_time_delay_min", delay);
        normalized.put("status_color", statusColor);
        normalized.put("passenger_advice", passengerAdvice);
        normalized.put("route_details", routeDetails);
        normalized.put("is_fallback", safeBoolean(raw.get("is_fallback"), false));

        return normalized;
    }

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
                bus.put("estimated_arrival_min", safeDouble(rawBus.get("estimated_arrival_min"), fallbackOrder * 10.0));
                bus.put("crowding_forecast", normalizeCrowdingForecast(rawBus.get("crowding_forecast")));
                bus.put("confidence", normalizeConfidence(rawBus.get("confidence")));

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

    private String normalizeStatusColor(Object value, double delay) {
        String color = safeString(value, "").toUpperCase();

        if (color.equals("RED") || color.equals("YELLOW") || color.equals("GREEN")) {
            return color;
        }

        if (delay > 8.0) {
            return "RED";
        }
        if (delay > 6.0) {
            return "YELLOW";
        }
        return "GREEN";
    }

    private String normalizeCrowdingStatus(Object value) {
        String status = safeString(value, "normal").trim().toLowerCase();

        if (status.equals("busy") || status.equals("quiet") || status.equals("normal")) {
            return status;
        }

        if (status.equals("crowded")) {
            return "busy";
        }
        if (status.equals("low")) {
            return "quiet";
        }

        return "normal";
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