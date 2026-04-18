package com.predictivetransit.backend.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AiEngineHealthIndicator: Custom health check for the Spring Boot Actuator.
 * Monitors the connectivity between the Java Backend and the Python AI Engine.
 * If the AI Engine is down, the overall health of the system might be affected.
 */
@Component
public class AiEngineHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    @Value("${python.api.url:http://localhost:8000}")
    private String pythonAiUrl;

    public AiEngineHealthIndicator() {
        // Set short timeouts for the health check to avoid blocking
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Checks the status of the AI Engine by calling its /health endpoint.
     * @return Health status (UP or DOWN) with detailed information.
     */
    @Override
    public Health health() {
        try {
            // Attempt to fetch the health status from the Python AI Service
            String response = restClient.get()
                    .uri(pythonAiUrl + "/health")
                    .retrieve()
                    .body(String.class);

            return Health.up()
                    .withDetail("aiEngine", "reachable")
                    .withDetail("endpoint", pythonAiUrl + "/health")
                    .withDetail("response", response)
                    .build();
        } catch (Exception e) {
            // If the connection fails, mark the health indicator as DOWN
            return Health.down()
                    .withDetail("aiEngine", "unreachable")
                    .withDetail("endpoint", pythonAiUrl + "/health")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}