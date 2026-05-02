package com.predictivetransit.backend.controller;

import com.predictivetransit.backend.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PredictionController: Endpoint where the Frontend receives AI prediction data via the Java Backend.
 * Communication flow: Frontend → Java Backend (:8080) → Python AI Engine (:8000)
 */
@RestController
@RequestMapping("/api/predict")
@Tag(name = "Prediction Monitoring API", description = "Fetches delay and next-bus predictions from the Python AI Engine")
@Validated
public class PredictionController {

    private static final String LINE_CODE_REGEX = "^L0[1-5]$";
    private static final String STOP_ID_REGEX = "^STP-L0[1-5]-\\d{2}$";

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    /**
     * Line-based delay prediction.
     * Example: GET /api/predict/L01
     * The Frontend calls this endpoint; the Backend fetches data from Python AI and returns it.
     */
    @Operation(summary = "Line-based delay prediction", description = "Returns the expected delay for a specified bus line (e.g., L01) at a given time.")
    @GetMapping("/{lineCode}")
    public Map<String, Object> getPrediction(
            @PathVariable
            @Pattern(regexp = LINE_CODE_REGEX, message = "lineCode must match L01-L05") String lineCode,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Integer minute) {
        return predictionService.getLinePrediction(lineCode, hour, minute);
    }

    /**
     * Next buses prediction.
     * Example: GET /api/predict/next-buses?lineCode=L01&stopId=STP-L01-05
     * Used for the "Next Buses" panel in the bottom sheet.
     */
    @Operation(summary = "Next buses prediction", description = "Returns the estimated arrival times of the next 3 buses for a specified line and stop ID.")
@GetMapping("/next-buses")
public Map<String, Object> getNextBuses(
    @RequestParam
    @Pattern(regexp = LINE_CODE_REGEX, message = "lineCode must match L01-L05") String lineCode,
    @RequestParam
    @Pattern(regexp = STOP_ID_REGEX, message = "stopId must match STP-LXX-YY") String stopId,
    @RequestParam(required = false)
    @Pattern(regexp = STOP_ID_REGEX, message = "destinationId must match STP-LXX-YY") String destinationId,
        @RequestParam(required = false) Integer hour,
        @RequestParam(required = false) Integer minute) {
    return predictionService.getNextBuses(lineCode, stopId, destinationId, hour, minute);
}
}