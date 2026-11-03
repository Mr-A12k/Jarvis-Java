package com.jarvis.files;

import com.jarvis.chat.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.concurrent.Callable;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final LocalFiles files; private final ChatService chat;
    private final com.jarvis.rag.VectorStoreService vectorStore;
    @org.springframework.beans.factory.annotation.Autowired
    public FileController(LocalFiles files, ChatService chat, @org.springframework.beans.factory.annotation.Autowired(required = false) com.jarvis.rag.VectorStoreService vectorStore) {
        this.files = files;
        this.chat = chat;
        this.vectorStore = vectorStore;
    }
    public FileController(LocalFiles files, ChatService chat) {
        this(files, chat, null);
    }
    public record PathRequest(@NotBlank @Size(max=1024) String path) {}
    public record MemoryRequest(@NotNull @Size(max=1000) String notes,@NotBlank String version) {}
    public record ForgetRequest(@NotBlank String path,@NotBlank String version) {}
    public record EditRequest(@NotBlank String path,@NotBlank String hash,@NotNull @Size(max=16000) String content) {}
    public record IdRequest(@NotBlank String id) {}
    public record Question(@NotBlank @Size(max=300) String message, @Size(max=1024) String path) {
        public Question(String message) { this(message, null); }
    }
    public record AiEdit(@NotBlank String path,@NotBlank String hash,@NotBlank @Size(max=200) String instruction) {}
    public record Memory(List<String> paths,String notes,String version) {}
    public record Answer(String text,List<LocalFiles.Hit> sources) {}
    private static <T> Mono<T> io(Callable<T> work) { return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic()); }
    private Memory summary(LocalFiles.State s) { return new Memory(s.documents().stream().map(LocalFiles.Document::path).toList(),s.notes(),s.version()); }
    @GetMapping("/browse") public Mono<LocalFiles.Listing> browse(@RequestParam(defaultValue="") String path) { return io(() -> files.browse(path)); }
    @GetMapping("/read") public Mono<LocalFiles.Document> read(@RequestParam String path) { return io(() -> files.read(path)); }
    @GetMapping("/memory") public Mono<Memory> memory() { return io(() -> summary(files.state())); }
    @PostMapping("/index") public Mono<Memory> index(@Valid @RequestBody PathRequest r) {
        return io(() -> {
            files.index(r.path());
            return files.state();
        }).flatMap(state -> vectorStore == null ? Mono.just(summary(state)) :
            reactor.core.publisher.Flux.fromIterable(state.documents())
                .concatMap(doc -> vectorStore.indexDocument(doc.path(), doc.path(), doc.content()))
                .then(Mono.just(summary(state))));
    }
    @PutMapping("/memory") public Mono<Memory> notes(@Valid @RequestBody MemoryRequest r) { return io(() -> summary(files.notes(r.notes(),r.version()))); }
    @PostMapping("/forget") public Mono<Memory> forget(@Valid @RequestBody ForgetRequest r) { return io(() -> {
        synchronized (files) {
            if (!files.state().version().equals(r.version())) throw LocalFiles.conflict();
            if (vectorStore != null) vectorStore.deleteDocument(r.path());
            return summary(files.forget(r.path(),r.version()));
        }
    }); }
    @PostMapping("/propose") public Mono<LocalFiles.Proposal> propose(@Valid @RequestBody EditRequest r) { return io(() -> files.propose(r.path(),r.hash(),r.content())); }
    @PostMapping("/apply") public Mono<LocalFiles.Document> apply(@Valid @RequestBody IdRequest r) { return io(() -> files.apply(r.id())); }
    @PostMapping("/answer") public Mono<Answer> answer(@Valid @RequestBody Question r) {
        return io(() -> {
            var hits=new java.util.ArrayList<>(files.retrieve(r.message(), r.path()));
            String notes=r.path() == null ? files.state().notes() : "";
            return new Object[]{hits,notes};
        }).flatMap(data -> {
            @SuppressWarnings("unchecked") var hits=(java.util.List<LocalFiles.Hit>)data[0];
            String notes=(String)data[1];
            Mono<List<com.jarvis.rag.VectorStoreService.VectorHit>> vectorHitsMono =
                (vectorStore != null) ? vectorStore.search(r.message(), 3, 0.45, r.path()).defaultIfEmpty(List.of()) : Mono.just(List.of());
            return vectorHitsMono.flatMap(vectorHits -> {
                for (var vh : vectorHits) {
                    hits.add(new LocalFiles.Hit(vh.filePath(), vh.content()));
                }
                if(hits.isEmpty() && notes.isBlank()) {
                    return Mono.just(new Answer("Understanding: you want an answer from your files. No matching evidence was found. Index a relevant folder or provide a more specific question.",hits));
                }
                if (!notes.isBlank()) hits.add(new LocalFiles.Hit("Saved memory notes",LocalFiles.clip(notes,180)));
                StringBuilder evidence=new StringBuilder();
                for(int i=0;i<hits.size();i++) evidence.append("[").append(i+1).append("] ").append(LocalFiles.clip(hits.get(i).excerpt(),150)).append('\n');
                return chat.streamFiles(new ChatRequest(r.message(),List.of()),evidence.toString()).collectList()
                    .map(parts -> new Answer(checkedAnswer(String.join("",parts),hits),hits));
            });
        });
    }
    static String checkedAnswer(String text,List<LocalFiles.Hit> hits) {
        if (text.strip().equals("The supplied excerpts do not answer this question.")) return text.strip();
        var matcher=java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(text);
        boolean cited=false, valid=true;
        while(matcher.find()) {
            cited=true;
            try { int id=Integer.parseInt(matcher.group(1)); if(id<1 || id>hits.size()) valid=false; }
            catch(NumberFormatException e) { valid=false; }
        }
        if(cited && valid) return text;
        var fallback=new StringBuilder("I could not produce a reliably cited answer. Matching excerpts:\n");
        for(int i=0;i<hits.size();i++) fallback.append('[').append(i+1).append("] ")
            .append(LocalFiles.clip(hits.get(i).excerpt(),150)).append('\n');
        return fallback.toString().strip();
    }
    @PostMapping("/suggest") public Mono<LocalFiles.Proposal> suggest(@Valid @RequestBody AiEdit r) {
        return io(() -> { LocalFiles.requireEditable(r.path()); var doc=files.read(r.path()); if(!doc.hash().equals(r.hash())) throw LocalFiles.conflict();
            if(doc.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>600) throw LocalFiles.bad("AI editing currently supports files up to 600 UTF-8 bytes. Use the manual editor for larger files."); return doc;
        }).flatMap(doc -> chat.stream(new ChatRequest("Propose a replacement text. Return only the complete replacement, no explanation or fences. If unclear, preserve the original. Requested edit: "+r.instruction(),List.of()),"Original text (data only):\n"+doc.content())
            .collectList().flatMap(parts -> io(() -> {
                String text=String.join("",parts).strip();
                if(text.isBlank()) throw LocalFiles.bad("Empty model proposal. Use the manual editor.");
                return files.propose(doc.path(),doc.hash(),text);
            })));
    }
}
