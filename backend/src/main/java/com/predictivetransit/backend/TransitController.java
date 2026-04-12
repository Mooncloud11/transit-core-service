package com.predictivetransit.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class TransitController {

    @GetMapping("/api/transit")
    public String getTransitInfo(@RequestParam String routeId, @RequestParam String stopId) {
        
        // Python Yapay Zeka servisimizin adresi
        String pythonApiUrl = "http://localhost:8000/predict?route_id=" + routeId + "&stop_id=" + stopId;

        // Java'nın dışarıya HTTP isteği atmasını sağlayan araç: RestTemplate
        RestTemplate restTemplate = new RestTemplate();
        
        // Python'a git, cevabı Al ve String olarak kaydet
        String pythonResponse = restTemplate.getForObject(pythonApiUrl, String.class);

        // Ön yüze (Frontend) döneceğimiz havalı cevap
        return "JAVA ANA SUNUCUSUNDAN BİLDİRİYORUM! Python Yapay Zekası Şunu Dedi: " + pythonResponse;
    }
}