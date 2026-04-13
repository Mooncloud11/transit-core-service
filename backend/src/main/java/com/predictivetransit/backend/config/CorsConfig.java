package com.predictivetransit.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS (Cross-Origin Resource Sharing): 
 * Farklı portlardan (Frontend-Backend) gelen isteklerin engellenmemesini sağlar.
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Tüm API uç noktalarını (endpoints) kapsar
                        .allowedOrigins("*") // Tüm kaynaklara izin ver (Hackathon modu)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}