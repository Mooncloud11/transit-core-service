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

@Component
public class DataInitializer implements CommandLineRunner {

    private final BusStopRepository busStopRepository;
    private final WeatherRepository weatherRepository;
    private final PassengerFlowRepository passengerFlowRepository;

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

        // Tarih formatlayıcı (CSV'deki "2025-03-01 08:30:00" formatını Java'ya çevirmek
        // için)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println(">>> DATA INITIALIZATION STARTED...");

        loadBusStopsIfEmpty("bus_stops.csv");
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

    private void loadBusStopsIfEmpty(String path) {
        if (busStopRepository.count() > 0) {
            System.out.println("-> Bus stops already exist, seed skipped.");
            return;
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
        // Durak İsimlerini frontend ile eşleşecek şekilde tanımlayalım
        java.util.Map<String, String[]> stopNamesMap = new java.util.HashMap<>();
        stopNamesMap.put("L01",
                new String[] { "Merkez Terminal", "Belediye Meydanı", "Cumhuriyet Caddesi", "Atatürk Bulvarı",
                        "Hürriyet Parkı", "Gül Mahallesi", "Çamlık Durağı", "Yeni Mahalle", "Sağlık Ocağı",
                        "Kültür Merkezi", "Stadyum", "Rektörlük", "Mühendislik Fakültesi", "Üniversite Kampüsü" });
        stopNamesMap.put("L02",
                new String[] { "Sanayi Sitesi", "Fabrikalar Bölgesi", "İş Merkezi", "Organize Sanayi", "Köprübaşı",
                        "Pazar Yeri", "Adliye", "Emniyet Müdürlüğü", "Devlet Hastanesi", "Acil Servis",
                        "Hastane Ana Giriş" });
        stopNamesMap.put("L03", new String[] { "Bağlar Mahallesi", "Bağlar Parkı", "Kooperatif", "Otogar", "PTT",
                "Çarşı Girişi", "Kapalı Çarşı", "Büyük Cami", "Çarşı Merkez" });
        stopNamesMap.put("L04",
                new String[] { "Esentepe Terminal", "Esentepe Parkı", "Yıldız Mahallesi", "Güneş Sokak", "Bahçelievler",
                        "Zafer Caddesi", "Kışla", "Spor Salonu", "AVM", "Postane", "Hükümet Konağı", "Meydan" });
        stopNamesMap.put("L05",
                new String[] { "Şehirlerarası Terminal", "Terminal Çıkışı", "Yeni Yol", "Kavşak", "Sanayi Kavşağı",
                        "Demir Çelik", "Lojmanlar", "İlkokul", "Ortaokul", "Lise", "Dershane Sokak", "Yurt",
                        "Spor Tesisleri", "Kütüphane", "Kampüs Girişi", "Kampüs Merkez" });

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

                    // Aynı durak ID si zaten eklendiyse atla (örneğin iki hat aynı durağı
                    // kullanıyorsa)
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
                    // Sadece ilk hatayı ekrana basalım ki terminal çöplüğe dönmesin
                    if (errorCount == 1) {
                        System.out.println("Bus stop parsing error example: " + e.getMessage());
                        System.out.println("Invalid bus stop row: " + line);
                    }
                }
            }
            System.out.println("-> Passenger flow loaded: " + count + " rows (failed rows: " + errorCount + ")");
        } catch (Exception e) {
           System.out.println("ERROR: Failed to read passenger flow data. " + e.getMessage());
        }
    }
}