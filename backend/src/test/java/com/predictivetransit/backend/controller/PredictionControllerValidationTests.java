package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.service.PredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PredictionController.class)
class PredictionControllerValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PredictionService predictionService;

    @Test
    void predictionRejectsInvalidLineCode() throws Exception {
        mockMvc.perform(get("/api/predict/ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nextBusesRejectsInvalidStopId() throws Exception {
        mockMvc.perform(get("/api/predict/next-buses")
                        .param("lineCode", "L01")
                        .param("stopId", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nextBusesRejectsInvalidDestinationId() throws Exception {
        mockMvc.perform(get("/api/predict/next-buses")
                        .param("lineCode", "L01")
                        .param("stopId", "STP-L01-01")
                        .param("destinationId", "BAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nextBusesAcceptsValidPayload() throws Exception {
        when(predictionService.getNextBuses("L01", "STP-L01-01", "STP-L01-05", null, null))
                .thenReturn(Map.of("line_id", "L01", "stop_id", "STP-L01-01", "next_buses", java.util.List.of()));

        mockMvc.perform(get("/api/predict/next-buses")
                        .param("lineCode", "L01")
                        .param("stopId", "STP-L01-01")
                        .param("destinationId", "STP-L01-05")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.line_id").value("L01"));
    }
}
