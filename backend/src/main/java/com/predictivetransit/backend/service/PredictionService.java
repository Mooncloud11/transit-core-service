package com.predictivetransit.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalTime; // EKLENDİ: Ömer'in istediği saat bilgisi için
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
        // Eski Python paketi (POST için kullanılıyordu, sistemin bozulmaması için yapıyı korudum)
        Map<String, Object> request = new HashMap<>();
        request.put("stop_id", stopId);
        request.put("temperature", temp);
        request.put("current_crowd", crowd);

        // --- EKLENEN KISIM: Ömer'in API'sinin beklediği format ---
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        
        // Veritabanı (Repository) hatası almamak için şimdilik varsayılan bir hat kodu atıyoruz. 
        // Sunumda L01 hattı üzerinden şov yapabilirsiniz.
        String lineCode = "L01"; 

        // Ömer'in FastAPI'sine gidecek dinamik URL
        String finalUrl = String.format("%s?line_code=%s&hour=%d&minute=%d", 
                                        PYTHON_ML_URL, lineCode, hour, minute);
        // ---------------------------------------------------------

        try {
            // ZORUNLU DEĞİŞİKLİK: Ömer'in sistemi artık POST değil GET bekliyor.
            // postForObject yerine getForObject yaptık ki sistem "405 Method Not Allowed" hatası vermesin.
            return restTemplate.getForObject(finalUrl, Map.class);
        } catch (Exception e) {
            // We return "Dummy" data to avoid errors if the Python service is not yet running.
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("estimated_delay", "Servis Hazır Değil");
            fallback.put("status", "Offline");
            return fallback;
        }
    }
}