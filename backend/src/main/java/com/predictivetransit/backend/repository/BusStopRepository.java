package com.predictivetransit.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.predictivetransit.backend.model.BusStop;

@Repository
public interface BusStopRepository extends JpaRepository<BusStop, String> {
    // ID tipi String çünkü CSV'de stop_id "STOP_101" gibi metinsel verilerdir.
}