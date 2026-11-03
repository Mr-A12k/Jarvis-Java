package com.jarvis.realtime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private final RealtimeFactService factService;
    private final RealtimeModelTrainingService trainingService;

    @Autowired
    public RealtimeController(
            RealtimeFactService factService,
            RealtimeModelTrainingService trainingService
    ) {
        this.factService = factService;
        this.trainingService = trainingService;
    }

    @GetMapping("/status")
    public RealtimeModelTrainingService.TrainingStatus getStatus() {
        return trainingService.getStatus();
    }

    @PostMapping("/train")
    public Map<String, Object> triggerTrain() {
        boolean started = trainingService.autoTrainModelInternal(true);
        return Map.of(
                "started", started,
                "message", started ? "Autonomous model retraining initiated." : "Retraining already in progress."
        );
    }

    @GetMapping("/temporal")
    public Map<String, Object> getTemporal(@RequestParam(defaultValue = "false") boolean tamil) {
        return Map.of(
                "summary", factService.getTemporalSummary(tamil)
        );
    }

    @GetMapping("/stocks")
    public Mono<Map<String, Object>> getStocks(@RequestParam(defaultValue = "") String query,
                                               @RequestParam(defaultValue = "false") boolean tamil) {
        return Mono.fromCallable(() -> factService.fetchStockMarketSnapshot(query, tamil))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(snapshot -> Map.of("snapshot", snapshot));
    }

    @GetMapping(value = "/chart", produces = "image/svg+xml")
    public Mono<String> getChartSvg(@RequestParam(defaultValue = "^NSEI") String symbol) {
        return Mono.fromCallable(() -> factService.getChartSvg(symbol))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @GetMapping(value = "/cinema-card", produces = "image/svg+xml")
    public String getCinemaCardSvg(
            @RequestParam(defaultValue = "New Release") String title,
            @RequestParam(defaultValue = "Netflix") String platform,
            @RequestParam(defaultValue = "This Week") String date,
            @RequestParam(defaultValue = "") String snippet
    ) {
        return factService.getCinemaCardSvg(title, platform, date, snippet);
    }
}
