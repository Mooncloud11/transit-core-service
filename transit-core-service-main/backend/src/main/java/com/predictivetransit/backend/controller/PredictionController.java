package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PredictionController: Frontend'in AI tahmin verilerini Java Backend üzerinden aldığı endpoint.
 * Frontend → Java Backend (:8080) → Python AI Engine (:8000)
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/predict")
@Tag(name = "Tahmin İzleme API", description = "Python AI Engine üzerinden gecikme ve sıradaki otobüs tahminlerini getirir")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * Hat bazlı gecikme tahmini.
     * Örnek: GET /api/predict/L01
     * Frontend bu endpoint'i çağırır, Backend Python AI'dan veriyi alıp döndürür.
     */
    @Operation(summary = "Hat bazlı gecikme tahmini", description = "Belirtilen otobüs hattı (örn. L01) için o anki beklenen tahmini gecikmeyi döndürür.")
    @GetMapping("/{lineCode}")
    public Map<String, Object> getPrediction(
            @PathVariable String lineCode,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Integer minute) {
        return predictionService.getLinePrediction(lineCode, hour, minute);
    }

    /**
     * Sıradaki otobüsler tahmini.
     * Örnek: GET /api/predict/next-buses?lineCode=L01&stopId=STP-L01-05
     * Bottom sheet'teki "Sıradaki Otobüsler" paneli için kullanılır.
     */
    @Operation(summary = "Sıradaki otobüsler tahmini", description = "Belirtilen otobüs hattı ve durak id'si için sıradaki 3 otobüsün tahmini geliş süresini döndürür.")
    @GetMapping("/next-buses")
    public Map<String, Object> getNextBuses(
            @RequestParam String lineCode,
            @RequestParam String stopId,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Integer minute) {
        return predictionService.getNextBuses(lineCode, stopId, hour, minute);
    }
}