package com.predictivetransit.backend.service;

import com.predictivetransit.backend.repository.BusStopRepository;
import com.predictivetransit.backend.repository.PassengerFlowRepository;
import com.predictivetransit.backend.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DataInitializerFailFastTests {

    @TempDir
    Path tempDir;

    @Test
    void runFailsFastWhenBusStopSeedProducesNoRows() throws IOException {
        Files.writeString(tempDir.resolve("bus_stops.csv"), "stop_id,line_id,route_id,stop_sequence,stop_lat,stop_lon\n");
        Files.writeString(tempDir.resolve("weather_observations.csv"), "timestamp,a,b,c,d,temperature_c,e,precipitation_mm,f,wind_speed_kmh\n");
        Files.writeString(tempDir.resolve("passenger_flow.csv"), "stop_id,a,b,hour_of_day,day_of_week,c,d,e,passenger_count\n");

        DataInitializer initializer = new DataInitializer(
                mock(BusStopRepository.class),
                mock(WeatherRepository.class),
                mock(PassengerFlowRepository.class));
        ReflectionTestUtils.setField(initializer, "externalDataDirectory", tempDir.toString());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> initializer.run());
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("No valid bus stop rows could be parsed"));
    }
}
