package com.predictivetransit.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PredictionServiceValidationTests {

    private final PredictionService predictionService = new PredictionService(new PredictionResponseNormalizer());

    @Test
    void getLinePredictionRejectsHourWithoutMinute() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> predictionService.getLinePrediction("L01", 12, null));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getNextBusesRejectsMinuteWithoutHour() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> predictionService.getNextBuses("L01", "STP-L01-05", null, null, 30));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getLinePredictionRejectsInvalidHourRange() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> predictionService.getLinePrediction("L01", 24, 0));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getNextBusesRejectsInvalidMinuteRange() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> predictionService.getNextBuses("L01", "STP-L01-05", null, 10, 60));

        assertEquals(400, ex.getStatusCode().value());
    }
}
