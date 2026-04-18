package com.predictivetransit.backend.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiEngineHealthIndicator implements HealthIndicator {

    private final RestClient restClient;

    @Value("${python.api.url:http://localhost:8000}")
    private String pythonAiUrl;

    public AiEngineHealthIndicator() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Health health() {
        try {
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
            return Health.down()
                    .withDetail("aiEngine", "unreachable")
                    .withDetail("endpoint", pythonAiUrl + "/health")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}