package com.predictivetransit.backend;

/**
 * DTO - Data Transfer Object: 
 * Used to encapsulate and transfer data between different microservices or layers.
 */
public class TransitResponse {
    private int predicted_arrival_minutes;
    private String crowd_level;
    private double confidence_score;
    private String message; 
    
    // Default No-Argument Constructor (Strictly required for JSON Deserialization)
    public TransitResponse() {}

    // Getters and Setters (For encapsulation and data access)
    public int getPredicted_arrival_minutes() { return predicted_arrival_minutes; }
    public void setPredicted_arrival_minutes(int predicted_arrival_minutes) { this.predicted_arrival_minutes = predicted_arrival_minutes; }

    public String getCrowd_level() { return crowd_level; }
    public void setCrowd_level(String crowd_level) { this.crowd_level = crowd_level; }

    public double getConfidence_score() { return confidence_score; }
    public void setConfidence_score(double confidence_score) { this.confidence_score = confidence_score; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}