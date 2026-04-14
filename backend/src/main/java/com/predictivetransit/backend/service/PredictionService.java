package com.predictivetransit.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

/**
 * PredictionService: The bridge between Java and Python (ML).
 * This service connects to the Python API and retrieves the prediction results.
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
     * Python sends data to the ML service and receives the predicted result.
     */
    public Map<String, Object> getPredictionFromPython(String stopId, double temp, int crowd) {
        // Python'a gönderilecek paket (JSON Payload)
        Map<String, Object> request = new HashMap<>();
        request.put("stop_id", stopId);
        request.put("temperature", temp);
        request.put("current_crowd", crowd);

        try {
            // We are sending a POST request to the Python API.
            return restTemplate.postForObject(PYTHON_ML_URL, request, Map.class);
        } catch (Exception e) {
            // We return "Dummy" data to avoid errors if the Python service is not yet running.
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("estimated_delay", "Servis Hazır Değil");
            fallback.put("status", "Offline");
            return fallback;
        }
    }
}