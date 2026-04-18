package com.predictivetransit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.predictivetransit.backend.model.PassengerFlow;

/**
 * PassengerFlowRepository: Data access layer for PassengerFlow entities.
 * Provides methods to interact with the passenger_flow table.
 */
@Repository
public interface PassengerFlowRepository extends JpaRepository<PassengerFlow, Long> {
}