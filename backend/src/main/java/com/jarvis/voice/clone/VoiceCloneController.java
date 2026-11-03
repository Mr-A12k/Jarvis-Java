package com.jarvis.voice.clone;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/voice/clone")
public class VoiceCloneController {

    private final VoiceCloneService service;

    public VoiceCloneController(VoiceCloneService service) {
        this.service = service;
    }

    public record EnrollRequest(
            @NotBlank String name,
            String refText,
            @NotBlank String audioBase64,
            String format
    ) {}

    public record SynthesizeRequest(
            String profileId,
            @NotBlank String text
    ) {}

    @GetMapping("/status")
    public Mono<Map<String, Object>> getStatus() {
        return Mono.fromCallable(service::getEngineStatus)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/profiles")
    public Mono<List<VoiceCloneProfile>> listProfiles() {
        return Mono.fromCallable(service::listProfiles)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/enroll")
    public Mono<ResponseEntity<VoiceCloneProfile>> enrollVoice(@Valid @RequestBody EnrollRequest request) {
        return Mono.fromCallable(() -> {
            String rawBase64 = request.audioBase64();
            if (rawBase64.contains(",")) {
                rawBase64 = rawBase64.substring(rawBase64.indexOf(",") + 1);
            }
            byte[] audioBytes = Base64.getDecoder().decode(rawBase64);
            VoiceCloneProfile profile = service.saveSample(
                    request.name(),
                    request.refText(),
                    audioBytes,
                    request.format()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(profile);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/profiles/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteProfile(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            boolean deleted = service.deleteProfile(id);
            if (deleted) {
                return ResponseEntity.ok(Map.<String, Object>of("success", true, "deletedId", id));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.<String, Object>of("success", false, "error", "Profile not found"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/synthesize")
    public Mono<ResponseEntity<VoiceCloneService.SynthesisResult>> synthesize(@Valid @RequestBody SynthesizeRequest request) {
        return Mono.fromCallable(() -> {
            VoiceCloneService.SynthesisResult result = service.synthesize(request.profileId(), request.text());
            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/audio/{fileName}")
    public Mono<ResponseEntity<Resource>> getAudio(@PathVariable String fileName) {
        return Mono.fromCallable(() -> {
            Path file = service.getAudioFile(fileName);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(resource);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/samples/{fileName}")
    public Mono<ResponseEntity<Resource>> getSample(@PathVariable String fileName) {
        return Mono.fromCallable(() -> {
            Path file = service.getSampleFile(fileName);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(resource);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
