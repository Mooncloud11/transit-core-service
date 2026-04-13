package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.model.BusStop;
import com.predictivetransit.backend.repository.BusStopRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST (Representational State Transfer) Controller: 
 * Bu sınıf, durak verilerini JSON formatında dış dünyaya açar.
 */
@RestController
@RequestMapping("/api/stops")
public class BusStopController {

    private final BusStopRepository busStopRepository;

    public BusStopController(BusStopRepository busStopRepository) {
        this.busStopRepository = busStopRepository;
    }

    @GetMapping
    public List<BusStop> getAllStops() {
        // Veritabanındaki tüm durakları liste olarak döner
        return busStopRepository.findAll();
    }
}