package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.model.BusStop;
import com.predictivetransit.backend.repository.BusStopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusStopController.class)
class BusStopControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BusStopRepository busStopRepository;

    @Test
    void getAllStopsReturnsRepositoryData() throws Exception {
        BusStop stop = new BusStop();
        stop.setId("L01::STP-L01-01");
        stop.setStopId("STP-L01-01");
        stop.setStopName("Merkez Terminal");
        stop.setStopLat(39.0);
        stop.setStopLon(35.0);
        stop.setLineId("L01");

        when(busStopRepository.findAll()).thenReturn(List.of(stop));

        mockMvc.perform(get("/api/stops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stopId").value("STP-L01-01"))
                .andExpect(jsonPath("$[0].stopName").value("Merkez Terminal"));
    }

    @Test
    void searchStopsReturnsMatchingResults() throws Exception {
        BusStop stop = new BusStop();
        stop.setId("L01::STP-L01-01");
        stop.setStopId("STP-L01-01");
        stop.setStopName("Merkez Terminal");
        stop.setStopLat(39.0);
        stop.setStopLon(35.0);
        stop.setLineId("L01");

        when(busStopRepository.findByStopNameContainingIgnoreCase("Merkez")).thenReturn(List.of(stop));

        mockMvc.perform(get("/api/stops/search").param("query", "Merkez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stopName").value("Merkez Terminal"));
    }
}
