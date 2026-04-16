package com.predictivetransit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.predictivetransit.backend.model.WeatherObservation;

@Repository
public interface WeatherRepository extends JpaRepository<WeatherObservation, Long> {
}