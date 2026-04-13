package com.predictivetransit.backend.service;

import com.predictivetransit.backend.model.*;
import com.predictivetransit.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class DataInitializer implements CommandLineRunner {

    private final BusStopRepository busStopRepository;
    private final WeatherRepository weatherRepository;
    private final PassengerFlowRepository passengerFlowRepository;

    public DataInitializer(BusStopRepository busStopRepository, 
                           WeatherRepository weatherRepository, 
                           PassengerFlowRepository passengerFlowRepository) {
        this.busStopRepository = busStopRepository;
        this.weatherRepository = weatherRepository;
        this.passengerFlowRepository = passengerFlowRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        String basePath = "backend/predictive_transit_data/";
        
        // Tarih formatlayıcı (CSV'deki "2025-03-01 08:30:00" formatını Java'ya çevirmek için)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        System.out.println(">>> VERİ YÜKLEME İŞLEMİ BAŞLIYOR...");

        loadBusStops(basePath + "bus_stops.csv");
        loadWeather(basePath + "weather_observations.csv", formatter);
        loadPassengerFlow(basePath + "stop_arrivals.csv", formatter); // Veya stop_arrivals.csv
        
        System.out.println(">>> TÜM VERİLER BAŞARIYLA YÜKLENDİ!");
    }

    private void loadBusStops(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); // Başlığı atla
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                if (v.length < 6) continue;
                try {
                    BusStop stop = new BusStop();
                    stop.setStopId(v[0]);
                    stop.setStopName(v[2]); 
                    stop.setStopLat(Double.parseDouble(v[4]));
                    stop.setStopLon(Double.parseDouble(v[5]));
                    busStopRepository.save(stop);
                    count++;
                } catch (Exception e) {
                    // Hatalı satırı atla
                }
            }
            System.out.println("-> Duraklar Yüklendi: " + count + " adet");
        } catch (Exception e) {
            System.out.println("HATA: Durak verisi okunamadı. " + e.getMessage());
        }
    }

    private void loadWeather(String path, DateTimeFormatter formatter) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine(); 
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                if (v.length < 10) continue;
                try {
                    WeatherObservation obs = new WeatherObservation();
                    obs.setTimestamp(LocalDateTime.parse(v[1], formatter)); // Index 1: timestamp
                    obs.setTemperature(Double.parseDouble(v[5]));           // Index 5: temperature_c
                    obs.setPrecipitation(Double.parseDouble(v[7]));         // Index 7: precipitation_mm
                    obs.setWindSpeed(Double.parseDouble(v[9]));             // Index 9: wind_speed_kmh
                    weatherRepository.save(obs);
                    count++;
                } catch (Exception e) {
                }
            }
            System.out.println("-> Hava Durumu Yüklendi: " + count + " adet");
        } catch (Exception e) {
            System.out.println("HATA: Hava durumu verisi okunamadı. " + e.getMessage());
        }
    }

    private void loadPassengerFlow(String path, DateTimeFormatter formatter) {
    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
        String header = br.readLine(); 
        System.out.println(">>> STOP_ARRIVALS BAŞLIK (HEADER): " + header);

        String line;
        int count = 0;
        int errorCount = 0;
        
        while ((line = br.readLine()) != null) {
            String[] v = line.split(",");
            if (v.length < 2) continue; 
            
            try {
                PassengerFlow flow = new PassengerFlow();
                flow.setStopId(v[0]); 
                flow.setTimestamp(LocalDateTime.now()); 
                
                // Hata muhtemelen burada: Sütun sayısına veya veri tipine (Double/Integer) takılıyor
                // Double olarak alıp int'e (Integer) çevirmeyi deniyoruz
                double passCount = Double.parseDouble(v[v.length - 1].trim());
                flow.setPassengerCount((int) passCount); 
                
                passengerFlowRepository.save(flow);
                count++;
            } catch (Exception e) {
                errorCount++;
                // Sadece ilk hatayı ekrana basalım ki terminal çöplüğe dönmesin
                if (errorCount == 1) {
                    System.out.println("HATA DETAYI (Örnek): " + e.getMessage());
                    System.out.println("HATALI SATIR: " + line);
                }
            }
        }
        System.out.println("-> Yolcu Akışı Yüklendi: " + count + " adet (Hata alınan satır: " + errorCount + ")");
    } catch (Exception e) {
        System.out.println("HATA: Dosya okunamadı. " + e.getMessage());
    }
}
}