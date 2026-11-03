package com.jarvis.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ChatRequest(@NotBlank @Size(max=32000) String message,
                          @Size(max=10) List<@NotNull @Valid Turn> history) {
    public enum Role { user, assistant }
    public record Turn(@NotNull Role role, @NotBlank @Size(max=32000) String content) {}
    public ChatRequest { history = history == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(history)); }
}
