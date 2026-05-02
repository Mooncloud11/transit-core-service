package com.predictivetransit.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * PassengerFlow Entity: Represents historical passenger volume data at bus stops.
 * This data is used by the AI engine to forecast crowding levels.
 */
@Entity
@Table(name = "passenger_flow")
@Data
public class PassengerFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime timestamp; // passenger_flow.csv -> timestamp
    
    private String stopId; // passenger_flow.csv -> stop_id
    
    private int passengerCount; // passenger_flow.csv -> passenger_count
    
}