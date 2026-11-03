package com.jarvis.chat;

import com.jarvis.config.JarvisProperties;
import com.jarvis.database.DatabaseStore;
import com.jarvis.files.LocalFiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class MemoryContextTest {
    @Test void savedRelationalMemoryReachesChatPrompt() throws Exception {
        var db=mock(DatabaseStore.class);
        var files=mock(LocalFiles.class);
        var chat=mock(ChatService.class);
        var config=mock(JarvisProperties.class);
        when(db.configured()).thenReturn(true);
        when(db.list(DatabaseStore.Kind.memories,10,0)).thenReturn(List.of(Map.of("title","Preference","content","Use concise answers")));
        when(files.state()).thenReturn(new LocalFiles.State(List.of(),"Local note","1"));
        when(chat.stream(any(),anyString())).thenReturn(Flux.just("Reply"));
        var controller=new ChatController(chat,config,files,db);
        assertThat(controller.complete(new ChatRequest("Help me",List.of())).block().text()).isEqualTo("Reply");
        verify(chat).stream(any(),argThat(context -> context.contains("Use concise answers") && context.contains("Local note")));
    }
}
