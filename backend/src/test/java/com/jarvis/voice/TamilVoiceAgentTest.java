package com.jarvis.voice;

import com.jarvis.voice.engine.SpeechRecognizer;
import com.jarvis.voice.engine.SpeechSynthesizer;
import com.jarvis.voice.model.SpeechRecognitionResult;
import com.jarvis.voice.tamil.TamilVoiceAgent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TamilVoiceAgentTest {

    @Test
    void recognizesTamilLanguageSupport() {
        var recognizer = mock(SpeechRecognizer.class);
        var synthesizer = mock(SpeechSynthesizer.class);
        var agent = new TamilVoiceAgent(recognizer, synthesizer);

        assertThat(agent.languageTag()).isEqualTo("ta-IN");
        assertThat(agent.displayName()).contains("Tamil");
        assertThat(agent.supports("ta-IN")).isTrue();
        assertThat(agent.supports("ta")).isTrue();
        assertThat(agent.supports("tamil")).isTrue();
        assertThat(agent.supports("en-US")).isFalse();
    }

    @Test
    void answersDirectTamilConversationalIntents() {
        var recognizer = mock(SpeechRecognizer.class);
        var synthesizer = mock(SpeechSynthesizer.class);
        var agent = new TamilVoiceAgent(recognizer, synthesizer);

        // Greetings
        assertThat(agent.answerDirectly("வணக்கம்").orElseThrow()).contains("ஜார்விஸ்");
        assertThat(agent.answerDirectly("vanakkam").orElseThrow()).contains("ஜார்விஸ்");

        // Identity in Tamil
        assertThat(agent.answerDirectly("நான் யார்?").orElseThrow()).contains("முதன்மை மற்றும் அங்கீகரிக்கப்பட்ட பயனர்");
        assertThat(agent.answerDirectly("naan yaar").orElseThrow()).contains("முதன்மை மற்றும் அங்கீகரிக்கப்பட்ட பயனர்");

        // Assistant Identity in Tamil
        assertThat(agent.answerDirectly("நீங்கள் யார்?").orElseThrow()).contains("ஜார்விஸ்");
        assertThat(agent.answerDirectly("neengal yaar").orElseThrow()).contains("ஜார்விஸ்");

        // Unmatched general query returns empty to allow LLM processing
        assertThat(agent.answerDirectly("மின்னணு அறிவியல் பற்றி கூறு")).isEmpty();
    }

    @Test
    void delegatesListenAndSpeakToInjectedEngines() {
        var recognizer = mock(SpeechRecognizer.class);
        var synthesizer = mock(SpeechSynthesizer.class);
        when(recognizer.recognize("ta-IN")).thenReturn(new SpeechRecognitionResult("வணக்கம்", 0.95, "ta-IN"));
        when(synthesizer.synthesize("பதில்", "ta-IN")).thenReturn(true);

        var agent = new TamilVoiceAgent(recognizer, synthesizer);

        var result = agent.listen();
        assertThat(result.text()).isEqualTo("வணக்கம்");
        assertThat(result.language()).isEqualTo("ta-IN");
        verify(recognizer).recognize("ta-IN");

        boolean spoken = agent.speak("பதில்");
        assertThat(spoken).isTrue();
        verify(synthesizer).synthesize("பதில்", "ta-IN");
    }
}
