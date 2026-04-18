package com.predictivetransit.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

// Burayı kendi paket adına göre düzelt

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "bus_stops")
@Data // Getter, Setter ve ToString'i otomatik oluşturur
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