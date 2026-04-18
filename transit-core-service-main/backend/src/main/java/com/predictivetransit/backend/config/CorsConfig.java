package com.predictivetransit.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing) Configuration:
 * Ensures that requests from different origins (e.g., Frontend on a different port) 
 * are not blocked by the browser.
 */
@Configuration
public class CorsConfig {

    /**
     * Configures global CORS mappings for the application.
     * @return a WebMvcConfigurer with CORS settings.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Apply CORS settings to all endpoints (/**)
                registry.addMapping("/**")
                        // Allow all origins (Hackathon mode - should be restricted in production)
                        .allowedOrigins("*")
                        // Allow standard HTTP methods
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}