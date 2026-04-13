package com.predictivetransit.backend.model;

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
    private String stopId;   // bus_stops.csv -> stop_id
    
    private String stopName; // bus_stops.csv -> stop_name
    
    private double stopLat;  // bus_stops.csv -> stop_lat
    
    private double stopLon;  // bus_stops.csv -> stop_lon
}