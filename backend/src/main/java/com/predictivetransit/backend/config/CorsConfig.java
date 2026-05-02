package com.predictivetransit.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS (Cross-Origin Resource Sharing) Configuration:
 * Ensures that requests from different origins (e.g., the frontend on a different port)
 * are not blocked by the browser.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://127.0.0.1:5500,http://localhost:5500}")
    private String allowedOriginsProperty;

    /**
     * Configures global CORS mappings for the application.
     * @return a WebMvcConfigurer with CORS settings.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new);

                // Apply CORS settings to all endpoints (/**)
                registry.addMapping("/**")
                        // Restrict API access to configured frontend origins.
                        .allowedOrigins(allowedOrigins)
                        // Allow standard HTTP methods
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}