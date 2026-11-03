package com.jarvis.ai;

import com.jarvis.common.ApiException;
import com.jarvis.config.JarvisProperties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Shared admission for this application's chat and embedding requests. */
@Component
public class ModelAdmission {
    private final Semaphore permits;
    private final int capacity;

    public ModelAdmission(JarvisProperties settings) {
        capacity = settings.concurrency();
        permits = new Semaphore(capacity);
    }

    public Lease chat() { return acquire(1, "MODEL_BUSY"); }
    public Lease embedding() { return acquire(capacity, "EMBEDDING_BUSY"); }

    private Lease acquire(int count, String code) {
        if (!permits.tryAcquire(count)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, code,
            "JARVIS is generating an answer or indexing documents. Wait for it to finish or stop the active request, then retry.");
        return new Lease(count);
    }

    public final class Lease implements AutoCloseable {
        private final int count;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(int count) { this.count = count; }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) permits.release(count);
        }
    }
}
