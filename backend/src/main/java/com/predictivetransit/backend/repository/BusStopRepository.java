package com.predictivetransit.backend.repository;

import com.predictivetransit.backend.model.BusStop;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BusStopRepository extends JpaRepository<BusStop, String> {
    
    // JPA Derived Query: İsminde (StopName) belirli bir metni (Containing) 
    // büyük/küçük harf duyarsız (IgnoreCase) olarak arar.
    List<BusStop> findByStopNameContainingIgnoreCase(String keyword);
    
}