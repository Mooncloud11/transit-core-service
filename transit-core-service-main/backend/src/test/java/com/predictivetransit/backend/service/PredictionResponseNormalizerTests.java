package com.predictivetransit.backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictionResponseNormalizerTests {

    private final PredictionResponseNormalizer normalizer = new PredictionResponseNormalizer();

    @Test
    void normalizeNextBusesUsesFallbackWhenListMissing() {
        Map<String, Object> raw = new HashMap<>();

        Map<String, Object> normalized = normalizer.normalizeNextBusesResponse(
                raw,
                "L01",
                "STP-L01-01",
                () -> Map.of("is_fallback", true, "next_buses", List.of()));

        assertEquals(true, normalized.get("is_fallback"));
        assertTrue(normalized.containsKey("next_buses"));
    }

    @Test
    void normalizeNextBusesClampsConfidenceAndMapsCrowding() {
        Map<String, Object> bus = new HashMap<>();
        bus.put("bus_order", 1);
        bus.put("estimated_arrival_min", 9.0);
        bus.put("crowding_forecast", "crowded");
        bus.put("confidence", 3.0);

        Map<String, Object> raw = new HashMap<>();
        raw.put("next_buses", List.of(bus));

        Map<String, Object> normalized = normalizer.normalizeNextBusesResponse(
                raw,
                "L01",
                "STP-L01-01",
                HashMap::new);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buses = (List<Map<String, Object>>) normalized.get("next_buses");

        assertEquals(1, buses.size());
        assertEquals("busy", buses.get(0).get("crowding_forecast"));
        assertEquals(1.0, buses.get(0).get("confidence"));
    }
}
