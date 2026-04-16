package com.predictivetransit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.predictivetransit.backend.model.PassengerFlow;

@Repository
public interface PassengerFlowRepository extends JpaRepository<PassengerFlow, Long> {
}