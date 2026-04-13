package com.predictivetransit.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * PredictionService: Java ve Python (ML) arasındaki köprü.
 * Bu servis, Python API'sine bağlanıp tahmin sonuçlarını getirir.
 */
@Service
public class PredictionService {

    private final RestTemplate restTemplate;
    // Python ekibinin sunucu adresi (Yarın onlardan teyit edeceğiz)
    private final String PYTHON_ML_URL = "http://localhost:8000/predict";

    public PredictionService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Python ML servisine veri gönderir ve tahmini sonucu alır.
     */
    public Map<String, Object> getPredictionFromPython(String stopId, double temp, int crowd) {
        // Python'a gönderilecek paket (JSON Payload)
        Map<String, Object> request = new HashMap<>();
        request.put("stop_id", stopId);
        request.put("temperature", temp);
        request.put("current_crowd", crowd);

        try {
            // Python API'sine POST isteği atıyoruz
            return restTemplate.postForObject(PYTHON_ML_URL, request, Map.class);
        } catch (Exception e) {
            // Python servisi henüz açık değilse hata dönmemesi için "Dummy" (Taslak) veri dönüyoruz
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("estimated_delay", "Servis Hazır Değil");
            fallback.put("status", "Offline");
            return fallback;
        }
    }
}