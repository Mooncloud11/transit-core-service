package com.predictivetransit.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_observations")
@Data
public class WeatherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Veritabanı için otomatik artan ID

    private LocalDateTime timestamp; // weather_observations.csv -> timestamp
    
    private double temperature; // weather_observations.csv -> temperature
    
    private double precipitation; // weather_observations.csv -> precipitation
    
    private double windSpeed; // weather_observations.csv -> wind_speed
}