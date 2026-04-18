package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.model.BusStop;
import com.predictivetransit.backend.repository.BusStopRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.List;



/**
 * BusStopController: Handles REST API requests related to bus stops.
 * Provides endpoints to retrieve all stops or search for specific ones.
 */
@CrossOrigin(origins = "*") 
@RestController
@RequestMapping("/api/stops")
public class BusStopController {

    private final BusStopRepository busStopRepository;

    public BusStopController(BusStopRepository busStopRepository) {
        this.busStopRepository = busStopRepository;
    }

    /**
     * Retrieves a list of all bus stops available in the database.
     * @return List of BusStop entities.
     */
    @GetMapping
    public List<BusStop> getAllStops() {
        return busStopRepository.findAll();
    }

    /**
     * Searches for bus stops by name.
     * Example: GET /api/stops/search?query=Center
     * @param query The search term for the stop name.
     * @return List of BusStop entities matching the search criteria.
     */
    @GetMapping("/search")
    public List<BusStop> searchStops(@RequestParam String query) {
        // Find stops where the name contains the query string (case-insensitive)
        return busStopRepository.findByStopNameContainingIgnoreCase(query);
    }
}