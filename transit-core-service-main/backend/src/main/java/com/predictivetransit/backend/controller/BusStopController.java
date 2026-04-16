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
 * REST (Representational State Transfer) Controller: 
 * Bu sınıf, durak verilerini JSON formatında dış dünyaya açar.
 */



@CrossOrigin(origins = "*") 
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
    /**
     * Örnek İstek: GET /api/stops/search?query=Merkez
     */
    @GetMapping("/search")
    public List<BusStop> searchStops(@RequestParam String query) {
        // Kullanıcıdan gelen "query" kelimesini alıp veritabanında aratır
        return busStopRepository.findByStopNameContainingIgnoreCase(query);
    }
}