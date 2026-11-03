package com.jarvis.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.common.ApiException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Single-user local workspace. No scripts, shell execution, or remote paths. */
@Service
public class LocalFiles {
    public record Document(String path, String content, String hash) {}
    public record Entry(String path, boolean directory) {}
    public record Listing(List<Entry> entries, boolean truncated) {}
    public record State(List<Document> documents, String notes, String version) {}
    public record Proposal(String id, String path, String before, String after, String hash) {}
    public record Hit(String path, String excerpt) {}
    private final Path storage;
    private final ObjectMapper json;
    private final Map<String,Proposal> proposals=new LinkedHashMap<>();
    private static final Set<String> BLOCKED=Set.of("windows","program files","program files (x86)","programdata","appdata",".ssh",".aws",".azure",".gnupg",".git",".codex",".agents",".tools","node_modules","target","data","$recycle.bin","system volume information");
    public LocalFiles(ObjectMapper json, @Value("${jarvis.file-memory-path:data/file-memory.json}") String storage) {
        this.json=json; this.storage=Path.of(storage).toAbsolutePath().normalize();
    }
    static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"FILE_REQUEST",message); }
    static ApiException conflict() { return new ApiException(HttpStatus.CONFLICT,"FILE_CHANGED","The file or memory changed. Reload and review a new proposal."); }
    private Path checked(String name, boolean file) throws IOException {
        if (name == null || name.startsWith("\\\\") || name.startsWith("//")) throw bad("Use a local absolute path.");
        Path path;
        try { path=Path.of(name); } catch (InvalidPathException e) { throw bad("Invalid path."); }
        if (!path.isAbsolute() || !path.normalize().equals(path)) throw bad("Use an absolute path without traversal segments.");
        Path current=path.getRoot();
        for (Path part:path) {
            String s=part.toString().toLowerCase(Locale.ROOT);
            if (BLOCKED.contains(s) || s.startsWith(".") || s.contains(":")) throw bad("System, hidden, credential and application data folders are excluded.");
            current=current.resolve(part);
            if (Files.isSymbolicLink(current) || Files.readAttributes(current,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).isOther()) throw bad("Linked files and junctions are excluded.");
        }
        if (path.toRealPath().startsWith(storage.getParent())) throw bad("Internal memory files cannot be edited here.");
        if (file) {
            String n=path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!(n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".docx") || n.endsWith(".pdf") || n.endsWith(".xlsx") || n.endsWith(".pptx")) || n.matches(".*(password|secret|credential|token|private.?key).*")) throw bad("Only non-credential .txt, .md, .docx, .pdf, .xlsx and .pptx files are supported.");
            int limit=fileLimit(path.toString());
            if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.size(path)>limit) throw bad("File exceeds its size limit (16 KB text or 2 MB document).");
        } else if (!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) throw bad("Choose a folder.");
        return path;
    }
    public synchronized Document read(String path) throws IOException {
        Path target=checked(path,true);
        try (var input=Files.newInputStream(target,LinkOption.NOFOLLOW_LINKS)) {
            int limit=fileLimit(path);
            byte[] bytes=input.readNBytes(limit+1);
            if (bytes.length>limit) throw bad("File exceeds its size limit.");
            if (isDocx(path)) {
                String content=DocxText.extract(bytes);
                return new Document(target.toString(),content,hash(content));
            }
            if (isPdf(path)) {
                String content=PdfText.extract(bytes);
                return new Document(target.toString(),content,hash(content));
            }
            if (isXlsx(path)) {
                String content=XlsxText.extract(bytes);
                return new Document(target.toString(),content,hash(content));
            }
            if (isPptx(path)) {
                String content=PptxText.extract(bytes);
                return new Document(target.toString(),content,hash(content));
            }
            String content=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (content.indexOf('\0')>=0) throw bad("Binary files are excluded.");
            return new Document(target.toString(),content,hash(content));
        }
    }
    public synchronized Listing browse(String folder) throws IOException {
        if (folder==null || folder.isBlank()) return new Listing(Arrays.stream(java.io.File.listRoots()).map(f -> new Entry(f.toString(),true)).toList(),false);
        Path root=checked(folder,false); var entries=new ArrayList<Entry>(); boolean truncated=false;
        try (var stream=Files.newDirectoryStream(root)) {
            int seen=0;
            for (Path p:stream) {
                if (++seen>2000 || entries.size()>=200) { truncated=true; break; }
                try { boolean dir=Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS); checked(p.toString(),!dir); entries.add(new Entry(p.toString(),dir)); }
                catch (IOException | ApiException e) { /* excluded or inaccessible */ }
            }
        }
        entries.sort(Comparator.comparing(Entry::directory).reversed().thenComparing(Entry::path));
        return new Listing(entries,truncated);
    }
    public synchronized State state() throws IOException {
        if (!Files.exists(storage)) return new State(List.of(),"","0");
        if (Files.size(storage)>4_000_000) throw bad("Memory store exceeds its size limit.");
        return json.readValue(Files.readString(storage),State.class);
    }
    private void save(State state) throws IOException {
        String serialized=json.writeValueAsString(state);
        if(serialized.getBytes(StandardCharsets.UTF_8).length>4_000_000) throw bad("Memory index is too large. Index a smaller folder.");
        Files.createDirectories(storage.getParent());
        Path temp=Files.createTempFile(storage.getParent(),"memory-",".tmp");
        try { Files.writeString(temp,serialized); Files.move(temp,storage,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    public synchronized State notes(String notes,String version) throws IOException {
        if (notes==null || notes.getBytes(StandardCharsets.UTF_8).length>1000) throw bad("Memory notes must fit within 1000 UTF-8 bytes.");
        State old=state(); if (!old.version().equals(version)) throw conflict();
        State next=new State(old.documents(),notes,UUID.randomUUID().toString()); save(next); return next;
    }
    public synchronized int index(String folder) throws IOException {
        Path root=checked(folder,false);
        if (root.getParent()==null) throw bad("Select a specific folder rather than a whole drive.");
        var docs=new LinkedHashMap<String,Document>(); State old=state();
        for (var d:old.documents()) if (!Path.of(d.path()).startsWith(root)) docs.put(d.path(),d);
        var queue=new ArrayDeque<Path>(); queue.add(root); int seen=0;
        while (!queue.isEmpty()) {
            Path dir=queue.removeFirst();
            try(var stream=Files.newDirectoryStream(dir)) {
                for(Path p:stream) {
                    if (++seen>2000) throw bad("Folder is too large. Select a smaller subfolder.");
                    try {
                        if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)) { checked(p.toString(),false); if(root.relativize(p).getNameCount()<6) queue.add(p); }
                        else { var doc=read(p.toString()); docs.put(doc.path(),doc); }
                    } catch(IOException | ApiException e) { /* Skip unsupported/inaccessible files. */ }
                    if(docs.size()>100) throw bad("Index is limited to 100 files. Forget files or select a smaller folder.");
                }
            }
        }
        save(new State(List.copyOf(docs.values()),old.notes(),UUID.randomUUID().toString())); return docs.size();
    }
    public synchronized State forget(String path,String version) throws IOException {
        State old=state(); if(!old.version().equals(version)) throw conflict();
        State next=new State(old.documents().stream().filter(d -> !d.path().equals(path)).toList(),old.notes(),UUID.randomUUID().toString()); save(next); return next;
    }
    public synchronized List<Hit> retrieve(String question) throws IOException {
        return retrieve(question, null);
    }
    public synchronized List<Hit> retrieve(String question, String path) throws IOException {
        var documents = state().documents();
        if (path != null && documents.stream().noneMatch(d -> d.path().equals(path)))
            throw bad("The selected file is not indexed. Reload the indexed files and select again.");
        Set<String> terms=new HashSet<>(Arrays.asList(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))); terms.removeIf(t -> t.length()<3);
        record Ranked(Hit hit,int score) {}
        var hits=new ArrayList<Ranked>();
        for(var saved:documents) {
            if (path != null && !saved.path().equals(path)) continue;
            try {
                var current=read(saved.path()); // Always use current bytes; deleted files never remain evidence.
                String[] lines=current.content().split("\\R");
                for(int i=0;i<lines.length;i++) {
                    String text=lines[i]; int score=0; String lower=text.toLowerCase(Locale.ROOT);
                    for(String term:terms) if(lower.contains(term)) score++;
                    if(score>0) hits.add(new Ranked(new Hit(current.path()+":"+(i+1),clip(text,220)),score));
                }
            } catch(IOException | ApiException e) { /* Unavailable evidence is not used. */ }
        }
        return hits.stream().sorted(Comparator.comparingInt(Ranked::score).reversed()).limit(3).map(Ranked::hit).toList();
    }
    public synchronized Proposal propose(String path,String expected,String content) throws IOException {
        requireEditable(path);
        if(content==null || content.getBytes(StandardCharsets.UTF_8).length>16000 || content.indexOf('\0')>=0) throw bad("Proposed text must be under 16 KB.");
        Document old=read(path); if(!old.hash().equals(expected)) throw conflict();
        if(proposals.size()>=20) proposals.remove(proposals.keySet().iterator().next());
        Proposal p=new Proposal(UUID.randomUUID().toString(),old.path(),old.content(),content,old.hash()); proposals.put(p.id(),p); return p;
    }
    public synchronized Document apply(String id) throws IOException {
        Proposal p=proposals.get(id); if(p==null) throw bad("Proposal expired. Create and review a new proposal.");
        requireEditable(p.path());
        Document current=read(p.path()); if(!current.hash().equals(p.hash())) throw conflict();
        Path target=checked(p.path(),true); Path backups=storage.getParent().resolve("backups"); Files.createDirectories(backups);
        Files.writeString(backups.resolve(id+".txt"),current.content(),StandardOpenOption.CREATE_NEW);
        Path temp=Files.createTempFile(target.getParent(),"jarvis-edit-",".tmp");
        try {
            Files.writeString(temp,p.after());
            if(!read(p.path()).hash().equals(p.hash())) throw conflict();
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
        proposals.remove(id); return read(p.path());
    }
    public static String clip(String value,int maxBytes) {
        StringBuilder b=new StringBuilder(); int used=0;
        for(int cp:value.codePoints().toArray()) { String c=new String(Character.toChars(cp)); int n=c.getBytes(StandardCharsets.UTF_8).length; if(used+n>maxBytes) break; b.append(c); used+=n; } return b.toString();
    }
    static boolean isDocx(String path) { return path != null && path.toLowerCase(Locale.ROOT).endsWith(".docx"); }
    static boolean isPdf(String path) { return path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf"); }
    static boolean isXlsx(String path) { return path != null && path.toLowerCase(Locale.ROOT).endsWith(".xlsx"); }
    static boolean isPptx(String path) { return path != null && path.toLowerCase(Locale.ROOT).endsWith(".pptx"); }
    private static int fileLimit(String path) { return (isDocx(path) || isPdf(path) || isXlsx(path) || isPptx(path)) ? 2_000_000 : 16000; }
    static void requireEditable(String path) { if(isDocx(path) || isPdf(path) || isXlsx(path) || isPptx(path)) throw bad("DOCX, PDF, XLSX and PPTX support text preview and indexing only. Edit the original in its document editor and reindex the folder."); }
    static String hash(String text) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
