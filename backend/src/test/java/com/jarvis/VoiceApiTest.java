package com.jarvis;
import com.jarvis.voice.VoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.util.Map;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"jarvis.auth-mode=token","jarvis.api-token=voice-test-token-01234567890123456789"})
@AutoConfigureWebTestClient
class VoiceApiTest {
    @Autowired WebTestClient client;
    @MockitoBean VoiceService voice;
    static final String AUTH="Bearer voice-test-token-01234567890123456789";
    @Test void microphoneCannotStartWithoutAuthentication() {
        client.post().uri("/api/v1/voice/listen").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(voice);
    }
    @Test void validatesSpeechBeforeLaunchingHelper() {
        client.post().uri("/api/v1/voice/speak").header("Authorization",AUTH).bodyValue(Map.of("text","x".repeat(2001)))
            .exchange().expectStatus().isBadRequest();
        verifyNoInteractions(voice);
    }
    @Test void textIsPassedAsData() throws Exception {
        String text="Hello; $(not a command)";
        when(voice.run("speak",text)).thenReturn(new ObjectMapper().readTree("{\"spoken\":true}"));
        client.post().uri("/api/v1/voice/speak").header("Authorization",AUTH).bodyValue(Map.of("text",text))
            .exchange().expectStatus().isOk().expectBody().jsonPath("$.spoken").isEqualTo(true);
        verify(voice).run("speak",text);
    }
}
