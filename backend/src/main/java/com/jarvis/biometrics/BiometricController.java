package com.jarvis.biometrics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * REST controller for Biometric Face & Voice Verification and Enrolment.
 */
@RestController
@RequestMapping("/api/v1/biometrics")
public class BiometricController {

    private final BiometricService service;

    public BiometricController(BiometricService service) {
        this.service = service;
    }

    public record EnrollFaceRequest(
            @NotBlank String userName,
            String role,
            @NotEmpty List<Double> embedding
    ) {}

    public record EnrollVoiceRequest(
            @NotBlank String userName,
            String role,
            @NotEmpty List<Double> embedding
    ) {}

    public record VerifyFaceRequest(
            @NotEmpty List<Double> embedding
    ) {}

    public record VerifyVoiceRequest(
            @NotEmpty List<Double> embedding
    ) {}

    public record VerifyMultimodalRequest(
            List<Double> faceEmbedding,
            List<Double> voiceEmbedding
    ) {}

    @GetMapping("/profiles")
    public Mono<List<BiometricProfile>> listProfiles() {
        return Mono.fromCallable(service::getAllProfiles)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/active")
    public Mono<BiometricProfile> getActiveUser() {
        return Mono.fromCallable(service::getActiveUser)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/active/reset")
    public Mono<BiometricProfile> resetActiveUser() {
        return Mono.fromCallable(() -> {
            service.resetActiveUserToMaster();
            return service.getActiveUser();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/enroll/face")
    public Mono<BiometricProfile> enrollFace(@Valid @RequestBody EnrollFaceRequest request) {
        return Mono.fromCallable(() -> service.enrollFace(request.userName(), request.role(), request.embedding()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/enroll/voice")
    public Mono<BiometricProfile> enrollVoice(@Valid @RequestBody EnrollVoiceRequest request) {
        return Mono.fromCallable(() -> service.enrollVoice(request.userName(), request.role(), request.embedding()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/verify/face")
    public Mono<BiometricMatchResult> verifyFace(@Valid @RequestBody VerifyFaceRequest request) {
        return Mono.fromCallable(() -> service.verifyFace(request.embedding()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/verify/voice")
    public Mono<BiometricMatchResult> verifyVoice(@Valid @RequestBody VerifyVoiceRequest request) {
        return Mono.fromCallable(() -> service.verifyVoice(request.embedding()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/verify/multimodal")
    public Mono<BiometricMatchResult> verifyMultimodal(@RequestBody VerifyMultimodalRequest request) {
        return Mono.fromCallable(() -> service.verifyMultimodal(request.faceEmbedding(), request.voiceEmbedding()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/profiles/{userName}")
    public Mono<ResponseEntity<Map<String, Object>>> deleteProfile(@PathVariable String userName) {
        return Mono.fromCallable(() -> {
            boolean deleted = service.deleteProfile(userName);
            if (deleted) {
                return ResponseEntity.ok(Map.<String, Object>of("deleted", true, "userName", userName));
            } else {
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body(Map.<String, Object>of("deleted", false, "error", "Profile not found"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
