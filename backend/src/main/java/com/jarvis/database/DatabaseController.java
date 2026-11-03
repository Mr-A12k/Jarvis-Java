package com.jarvis.database;

import com.jarvis.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.jarvis.database.DatabaseStore.Kind;

@RestController
@RequestMapping("/api/v1/database")
public class DatabaseController {
    private final DatabaseStore store;
    public DatabaseController(DatabaseStore store) { this.store=store; }
    public record RecordInput(@NotBlank @Size(max=200) String title,@Size(max=8000) String content) {}
    public record UpdateInput(@Min(1) int version,@NotBlank @Size(max=200) String title,@Size(max=8000) String content) {}
    public record TurnInput(@NotNull @Pattern(regexp="user|assistant") String role,@NotBlank @Size(max=16000) String content) {}
    public record Snapshot(@NotBlank @Size(max=200) String title,@NotEmpty @Size(max=100) List<@NotNull @Valid TurnInput> messages) {}
    private static <T> Mono<T> io(Callable<T> work) { return Mono.fromCallable(work).subscribeOn(Schedulers.boundedElastic()); }
    private static void valid(boolean condition) {
        if(!condition) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Check content, version and pagination limits.");
    }
    private static void content(Kind kind,String content) { valid(kind!=Kind.memories || (content!=null && !content.isBlank())); }
    @GetMapping("/status") public Mono<Map<String,Object>> status() {
        return io(() -> { if(!store.configured()) return Map.of("configured",false,"ready",false); store.check(); return Map.of("configured",true,"ready",true); });
    }
    @GetMapping("/{kind}") public Mono<List<Map<String,Object>>> list(@PathVariable Kind kind,@RequestParam(defaultValue="30") int limit,@RequestParam(defaultValue="0") int offset) {
        valid(limit>=1 && limit<=100 && offset>=0 && offset<=100000); return io(() -> store.list(kind,limit,offset));
    }
    @GetMapping("/memories/export") public Mono<List<Map<String,Object>>> exportMemories() {
        return io(store::exportMemories);
    }
    @PostMapping("/{kind}") public Mono<Map<String,Object>> create(@PathVariable Kind kind,@Valid @RequestBody RecordInput r) {
        content(kind,r.content()); return io(() -> store.create(kind,r.title(),r.content()));
    }
    @PutMapping("/{kind}/{id}") public Mono<Map<String,Object>> update(@PathVariable Kind kind,@PathVariable UUID id,@Valid @RequestBody UpdateInput r) {
        content(kind,r.content()); return io(() -> store.update(kind,id,r.version(),r.title(),r.content()));
    }
    @DeleteMapping("/{kind}/{id}") public Mono<Map<String,Boolean>> delete(@PathVariable Kind kind,@PathVariable UUID id,@RequestParam int version) {
        valid(version>=1); return io(() -> { store.delete(kind,id,version); return Map.of("deleted",true); });
    }
    @GetMapping("/conversations/{id}/messages") public Mono<List<Map<String,Object>>> messages(@PathVariable UUID id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        valid(after>=0 && limit>=1 && limit<=100); return io(() -> store.messages(id,after,limit));
    }
    @PostMapping("/snapshots") public Mono<Map<String,Object>> save(@Valid @RequestBody Snapshot r) {
        return io(() -> store.save(r.title(),r.messages().stream().map(t -> new DatabaseStore.Turn(t.role(),t.content())).toList()));
    }
}
