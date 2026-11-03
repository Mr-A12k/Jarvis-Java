package com.jarvis.system;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SystemHardwareService {

    public record MemoryMetric(long totalBytes, long freeBytes, long usedBytes, String totalFormatted, String freeFormatted, String usedFormatted) {}
    public record GpuMetric(String name, int totalVramMb, int freeVramMb, int usedVramMb, String totalFormatted, String freeFormatted, String usedFormatted) {}
    public record HardwareSnapshot(MemoryMetric ram, GpuMetric gpu, MemoryMetric rom, MemoryMetric heap) {}

    private final AtomicReference<CachedGpu> cachedGpu = new AtomicReference<>(null);

    private record CachedGpu(GpuMetric metric, long timestamp) {}

    public HardwareSnapshot snapshot() {
        return new HardwareSnapshot(getRam(), getGpu(), getRom(), getHeap());
    }

    public MemoryMetric getRam() {
        try {
            var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long total = osBean.getTotalMemorySize();
            long free = osBean.getFreeMemorySize();
            long used = Math.max(0, total - free);
            return new MemoryMetric(total, free, used, formatGb(total), formatGb(free), formatGb(used));
        } catch (Exception e) {
            long totalFallback = 6L * 1024 * 1024 * 1024;
            return new MemoryMetric(totalFallback, totalFallback / 4, (totalFallback * 3) / 4,
                    formatGb(totalFallback), formatGb(totalFallback / 4), formatGb((totalFallback * 3) / 4));
        }
    }

    public MemoryMetric getRom() {
        try {
            File root = new File(".").getAbsoluteFile();
            while (root.getParentFile() != null) {
                root = root.getParentFile();
            }
            long total = root.getTotalSpace();
            long free = root.getUsableSpace();
            long used = Math.max(0, total - free);
            return new MemoryMetric(total, free, used, formatGb(total), formatGb(free), formatGb(used));
        } catch (Exception e) {
            return new MemoryMetric(0, 0, 0, "—", "—", "—");
        }
    }

    public MemoryMetric getHeap() {
        var runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        return new MemoryMetric(max, max - used, used, formatMb(max), formatMb(max - used), formatMb(used));
    }

    public GpuMetric getGpu() {
        long now = System.currentTimeMillis();
        CachedGpu cached = cachedGpu.get();
        if (cached != null && (now - cached.timestamp) < 5000) {
            return cached.metric;
        }

        GpuMetric fresh = queryNvidiaGpu();
        if (fresh == null) {
            fresh = queryWmiGpu();
        }
        if (fresh == null) {
            fresh = new GpuMetric("Integrated / Generic GPU", 0, 0, 0, "—", "—", "—");
        }

        cachedGpu.set(new CachedGpu(fresh, now));
        return fresh;
    }

    private GpuMetric queryNvidiaGpu() {
        try {
            Process process = new ProcessBuilder("nvidia-smi", "--query-gpu=name,memory.total,memory.free,memory.used",
                    "--format=csv,noheader,nounits")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 4) {
                        String name = parts[0].trim();
                        int totalMb = Integer.parseInt(parts[1].trim());
                        int freeMb = Integer.parseInt(parts[2].trim());
                        int usedMb = Integer.parseInt(parts[3].trim());
                        return new GpuMetric(name, totalMb, freeMb, usedMb,
                                formatMb((long) totalMb * 1024 * 1024),
                                formatMb((long) freeMb * 1024 * 1024),
                                formatMb((long) usedMb * 1024 * 1024));
                    }
                }
            }
        } catch (Exception ignored) {
            // nvidia-smi not available or errored
        }
        return null;
    }

    private GpuMetric queryWmiGpu() {
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Get-CimInstance Win32_VideoController | Select-Object -First 1 Name, AdapterRAM | ForEach-Object { \"$($_.Name)|$($_.AdapterRAM)\" }")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line != null && line.contains("|")) {
                    String[] parts = line.split("\\|");
                    String name = parts[0].trim();
                    long ramBytes = parts.length > 1 ? Long.parseLong(parts[1].trim()) : 0;
                    int ramMb = (int) (ramBytes / (1024 * 1024));
                    return new GpuMetric(name, ramMb, ramMb, 0, formatMb(ramBytes), formatMb(ramBytes), "0 MB");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String generateEnglishSummary() {
        var snap = snapshot();
        return String.format(Locale.ENGLISH,
                "System Resources Status:\n" +
                "• RAM: %s available (Total: %s, Used: %s)\n" +
                "• GPU: %s — %s VRAM available (Total: %s)\n" +
                "• ROM / Storage: %s available (Total: %s)\n" +
                "• Java Heap: %s used (Max Limit: %s)",
                snap.ram.freeFormatted, snap.ram.totalFormatted, snap.ram.usedFormatted,
                snap.gpu.name, snap.gpu.freeFormatted, snap.gpu.totalFormatted,
                snap.rom.freeFormatted, snap.rom.totalFormatted,
                snap.heap.usedFormatted, snap.heap.totalFormatted);
    }

    public String generateTamilSummary() {
        var snap = snapshot();
        return String.format(Locale.ROOT,
                "கணினி வளங்கள் (System Resources):\n" +
                "• RAM: %s காலியாக உள்ளது (மொத்தம்: %s, பயன்பாட்டில்: %s)\n" +
                "• GPU: %s — %s VRAM கிடைக்கிறது (மொத்தம்: %s)\n" +
                "• ROM / சேமிப்பகம்: %s காலியாக உள்ளது (மொத்தம்: %s)\n" +
                "• Java Heap நினைவகம்: %s பயன்பாட்டில் உள்ளது (அதிகபட்ச வரம்பு: %s)",
                snap.ram.freeFormatted, snap.ram.totalFormatted, snap.ram.usedFormatted,
                snap.gpu.name, snap.gpu.freeFormatted, snap.gpu.totalFormatted,
                snap.rom.freeFormatted, snap.rom.totalFormatted,
                snap.heap.usedFormatted, snap.heap.totalFormatted);
    }

    private static String formatGb(long bytes) {
        double gb = (double) bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(Locale.ENGLISH, "%.2f GB", gb);
    }

    private static String formatMb(long bytes) {
        double mb = (double) bytes / (1024.0 * 1024.0);
        return String.format(Locale.ENGLISH, "%.0f MB", mb);
    }
}
