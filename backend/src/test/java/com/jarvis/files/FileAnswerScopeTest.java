package com.jarvis.files;

import com.jarvis.chat.ChatService;
import com.jarvis.rag.VectorStoreService;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileAnswerScopeTest {
    @Test void selectedFileWithoutEvidenceDoesNotFallBackToPersonalNotes() throws Exception {
        var files=mock(LocalFiles.class);
        var chat=mock(ChatService.class);
        var vectors=mock(VectorStoreService.class);
        when(files.retrieve("deadline", "selected.md")).thenReturn(List.of());
        when(vectors.search("deadline",3,0.45,"selected.md")).thenReturn(Mono.just(List.of()));
        var answer=new FileController(files,chat,vectors)
            .answer(new FileController.Question("deadline","selected.md")).block();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.text()).contains("No matching evidence");
        verify(files,never()).state();
        verifyNoInteractions(chat);
    }
}
