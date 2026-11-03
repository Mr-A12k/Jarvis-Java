package com.jarvis.security;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {
    private final String mode;
    public SessionController(@Value("${jarvis.auth-mode:local}") String mode) { this.mode=mode; }
    @GetMapping("/api/v1/session") public Map<String,String> session() { return Map.of("mode",mode); }
}
