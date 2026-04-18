package com.predictivetransit.backend.service;

import com.predictivetransit.backend.model.*;
import com.predictivetransit.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import java.nio.file.Files;
import java.nio.file.Path;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DataInitializer: Responsible for seeding the database with initial data from
 * CSV files.
 * Implements CommandLineRunner to execute logic on application startup.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final BusStopRepository busStopRepository;
    private final WeatherRepository weatherRepository;
    private final PassengerFlowRepository passengerFlowRepository;

    // Optional external directory for data files (configured in
    // application.properties)
    @Value("${transit.data.directory:}")
    private String externalDataDirectory;

    public DataInitializer(BusStopRepository busStopRepository,
            WeatherRepository weatherRepository,
            PassengerFlowRepository passengerFlowRepository) {
        this.busStopRepository = busStopRepository;
        this.weatherRepository = weatherRepository;
        this.passengerFlowRepository = passengerFlowRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Date formatter for parsing timestamps in the CSV files (e.g., "2025-03-01
        // 08:30:00")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println(">>> DATA INITIALIZATION STARTED...");

        // Load data from CSV if the corresponding database tables are empty
        refreshBusStops("bus_stops.csv");
        loadWeatherIfEmpty("weather_observations.csv", formatter);
        loadPassengerFlowIfEmpty("passenger_flow.csv");

        System.out.println(">>> ALL DATA LOADED SUCCESSFULLY!");
    }

    private LocalDateTime buildPassengerFlowTimestamp(String[] v) {
        int hourOfDay = Integer.parseInt(v[3].trim());
        int dayOfWeek = Integer.parseInt(v[4].trim());

        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new IllegalArgumentException("Invalid hour_of_day value: " + hourOfDay);
        }

        if (dayOfWeek < 0 || dayOfWeek > 6) {
            throw new IllegalArgumentException("Invalid day_of_week value: " + dayOfWeek);
        }

        LocalDate referenceWeekStart = LocalDate.of(2025, 1, 6);

        return referenceWeekStart
                .plusDays(dayOfWeek)
                .atTime(hourOfDay, 0);
    }

    private void refreshBusStops(String path) {
        long existing = busStopRepository.count();
        if (existing > 0) {
            System.out.println("-> Refreshing bus stops to sync canonical stop names and coordinates.");
            busStopRepository.deleteAll();
        }
        loadBusStops(path);
    }

    private BufferedReader openDataFile(String fileName) throws Exception {
        Resource resource = resolveDataResource(fileName);
        return new BufferedReader(new InputStreamReader(resource.getInputStream()));
    }

    private Resource resolveDataResource(String fileName) {
        if (externalDataDirectory != null && !externalDataDirectory.trim().isEmpty()) {
            Path externalPath = Path.of(externalDataDirectory, fileName);
            if (Files.exists(externalPath)) {
                return new org.springframework.core.io.FileSystemResource(externalPath);
            }

            throw new IllegalStateException("External data file not found: " + externalPath);
        }

        return new ClassPathResource("data/" + fileName);
    }

    private void loadWeatherIfEmpty(String path, DateTimeFormatter formatter) {
        if (weatherRepository.count() > 0) {
            System.out.println("-> Weather data already exists, seed skipped.");
            return;
        }
        loadWeather(path, formatter);
    }

    private void loadPassengerFlowIfEmpty(String path) {
        if (passengerFlowRepository.count() > 0) {
            System.out.println("-> Passenger flow data already exists, seed skipped.");
            return;
        }
        loadPassengerFlow(path);
    }

    private void loadBusStops(String path) {
        // Map stop names to line IDs to match frontend expectations
        java.util.Map<String, String[]> stopNamesMap = new java.util.HashMap<>();
        stopNamesMap.put("L01",
                new String[] { "Central Terminal", "City Hall Square", "Republic Avenue", "Ataturk Boulevard",
                        "Liberty Park", "Gul Neighborhood", "Camlik Stop", "New Neighborhood", "Health Center",
                        "Cultural Center", "Stadium", "Rectorate", "Faculty of Engineering", "University Campus" });
        stopNamesMap.put("L02",
                new String[] { "Industrial Site", "Factories Zone", "Business Center", "Organized Industry",
                        "Bridgehead",
                        "Market Place", "Courthouse", "Police Headquarters", "State Hospital", "Emergency Service",
                        "Hospital Main Entrance" });
        stopNamesMap.put("L03",
                new String[] { "Baglar Neighborhood", "Baglar Park", "Cooperative", "Bus Station", "PTT",
                        "Bazaar Entrance", "Grand Bazaar", "Grand Mosque", "Bazaar Center" });
        stopNamesMap.put("L04",
                new String[] { "Esentepe Terminal", "Esentepe Park", "Yildiz Neighborhood", "Gunes Street",
                        "Bahcelievler",
                        "Victory Avenue", "Barracks", "Sports Hall", "Shopping Mall", "Post Office", "Government House",
                        "Square" });
        stopNamesMap.put("L05",
                new String[] { "Intercity Terminal", "Terminal Exit", "New Road", "Intersection", "Industrial Junction",
                        "Iron & Steel", "Staff Housing", "Primary School", "Middle School", "High School",
                        "Private Course Street", "Dormitory",
                        "Sports Facilities", "Library", "Campus Entrance", "Campus Center" });

        try (BufferedReader br = openDataFile(path)) {
            br.readLine(); // Başlığı atla
            String line;
            int count = 0;
            int errorCount = 0;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                if (v.length < 6)
                    continue;
                try {
                    String stopId = v[0];
                    String lineId = v[1];
                    String recordId = lineId + "::" + stopId;
                    int sequence = Integer.parseInt(v[3]);

                    String stopName = "unknown stop";
                    if (stopNamesMap.containsKey(lineId)) {
                        String[] names = stopNamesMap.get(lineId);
                        if (sequence > 0 && sequence <= names.length) {
                            stopName = names[sequence - 1];
                        }
                    }

                    // Skip if the record already exists (e.g., stop shared by multiple lines)
                    if (busStopRepository.existsById(recordId)) {
                        continue;
                    }

                    BusStop stop = new BusStop();
                    stop.setId(recordId);
                    stop.setStopId(stopId);
                    stop.setStopName(stopName);
                    stop.setStopLat(Double.parseDouble(v[4]));
                    stop.setStopLon(Double.parseDouble(v[5]));
                    stop.setLineId(lineId);
                    busStopRepository.save(stop);
                    count++;
                } catch (Exception e) {
                    errorCount++;
                    if (errorCount == 1) {
                        System.out.println("Bus stop parsing error example: " + e.getMessage());
                        System.out.println("Invalid bus stop row: " + line);
                    }
                }
            }
            System.out.println("-> Bus stops loaded: " + count + " rows (failed rows: " + errorCount + ")");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to read bus stop data. " + e.getMessage());
        }
    }

    private void loadWeather(String path, DateTimeFormatter formatter) {
        try (BufferedReader br = openDataFile(path)) {
            br.readLine();
            String line;
            int count = 0;
            int errorCount = 0;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                if (v.length < 10)
                    continue;
                try {
                    WeatherObservation obs = new WeatherObservation();
                    obs.setTimestamp(LocalDateTime.parse(v[1], formatter)); // Index 1: timestamp
                    obs.setTemperature(Double.parseDouble(v[5])); // Index 5: temperature_c
                    obs.setPrecipitation(Double.parseDouble(v[7])); // Index 7: precipitation_mm
                    obs.setWindSpeed(Double.parseDouble(v[9])); // Index 9: wind_speed_kmh
                    weatherRepository.save(obs);
                    count++;
                } catch (Exception e) {
                    errorCount++;
                    if (errorCount == 1) {
                        System.out.println("Weather parsing error example: " + e.getMessage());
                        System.out.println("Invalid weather row: " + line);
                    }
                }
            }
            System.out.println("-> Weather observations loaded: " + count + " rows (failed rows: " + errorCount + ")");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to read weather data. " + e.getMessage());
        }
    }

    private void loadPassengerFlow(String path) {
        try (BufferedReader br = openDataFile(path)) {
            String header = br.readLine();
            System.out.println(">>> PASSENGER_FLOW HEADER: " + header);

            String line;
            int count = 0;
            int errorCount = 0;

            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                if (v.length < 9)
                    continue;

                try {
                    PassengerFlow flow = new PassengerFlow();
                    flow.setStopId(v[0]);
                    flow.setTimestamp(buildPassengerFlowTimestamp(v));

                    double passCount = Double.parseDouble(v[8].trim());
                    flow.setPassengerCount((int) passCount);

                    passengerFlowRepository.save(flow);
                    count++;
                } catch (Exception e) {
                    errorCount++;
                    // Only log the first error to avoid terminal clutter
                    if (errorCount == 1) {
                        System.out.println("Passenger flow parsing error example: " + e.getMessage());
                        System.out.println("Invalid row: " + line);
                    }
                }
            }
            System.out.println("-> Passenger flow loaded: " + count + " rows (failed rows: " + errorCount + ")");
        } catch (Exception e) {
            System.out.println("ERROR: Failed to read passenger flow data. " + e.getMessage());
        }
    }
}