package com.predictivetransit.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * WeatherObservation Entity: Stores historical weather data.
 * Used by the AI model to correlate weather conditions with transit delays.
 */
@Entity
@Table(name = "weather_observations")
@Data
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Auto-incremented primary key for the database

    private LocalDateTime timestamp; // Maps to CSV: timestamp
    
    private double temperature; // Maps to CSV: temperature
    
    private double precipitation; // Maps to CSV: precipitation
    
    private double windSpeed; // Maps to CSV: wind_speed
}