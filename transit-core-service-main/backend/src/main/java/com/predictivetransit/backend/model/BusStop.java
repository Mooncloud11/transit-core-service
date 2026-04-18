package com.predictivetransit.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * BusStop Entity: Represents a bus stop in the transit system.
 * This class is mapped to the "bus_stops" table in the database.
 */
@Entity
@Table(name = "bus_stops")
@Data // Automatically generates Getters, Setters, and ToString via Lombok
public class BusStop {

    @Id
    @JsonIgnore
    private String id;

    private String stopId;

    private String stopName;

    private double stopLat;

    private double stopLon;

    private String lineId;
}