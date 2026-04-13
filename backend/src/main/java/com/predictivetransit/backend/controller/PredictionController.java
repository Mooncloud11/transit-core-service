package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.service.PredictionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PredictionController: Batuhan'ın (Frontend) tahmin isteği atacağı son durak.
 */
@RestController
@RequestMapping("/api/predict")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * Örnek İstek: GET /api/predict/STP-L01-01?temp=22.5&crowd=40
     */
    @GetMapping("/{stopId}")
    public Map<String, Object> getPrediction(@PathVariable String stopId,
                                            @RequestParam(defaultValue = "20.0") double temp,
                                            @RequestParam(defaultValue = "30") int crowd) {
        
        // Python ML Köprüsünü kullanarak tahmini getiriyoruz
        return predictionService.getPredictionFromPython(stopId, temp, crowd);
    }
}