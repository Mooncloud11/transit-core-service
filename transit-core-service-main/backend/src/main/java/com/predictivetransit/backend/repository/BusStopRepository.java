package com.predictivetransit.backend.repository;

import com.predictivetransit.backend.model.BusStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * BusStopRepository: Data access layer for BusStop entities.
 * Extends JpaRepository to provide standard CRUD operations.
 */
public interface BusStopRepository extends JpaRepository<BusStop, String> {

    /**
     * Searches for bus stops whose name contains the specified keyword, ignoring case.
     * @param keyword The name or part of the name to search for.
     * @return A list of matching BusStop entities.
     */
    List<BusStop> findByStopNameContainingIgnoreCase(String keyword);

    List<BusStop> findAllByStopId(String stopId);

    BusStop findByStopIdAndLineId(String stopId, String lineId);

}