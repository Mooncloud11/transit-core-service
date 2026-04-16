package com.predictivetransit.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class TransitController {
    @Value("${python.api.url}")
    private String pythonBaseUrl;
   @GetMapping("/api/transit")
public TransitResponse getTransitInfo(@RequestParam String routeId, @RequestParam String stopId) {
    String pythonApiUrl = pythonBaseUrl + "/predict?route_id=" + routeId + "&stop_id=" + stopId;
    RestTemplate restTemplate = new RestTemplate();

    try {
        // RestTemplate automatically maps the incoming JSON to our Java Object (Deserialization)
        return restTemplate.getForObject(pythonApiUrl, TransitResponse.class);
    } catch (Exception e) {
        // Fallback mechanism: Return a graceful error object if the AI Engine is unreachable
        TransitResponse errorResponse = new TransitResponse();
        errorResponse.setMessage("ERROR: AI Engine is currently unreachable. Details: " + e.getMessage());
        return errorResponse;
    }
}
}