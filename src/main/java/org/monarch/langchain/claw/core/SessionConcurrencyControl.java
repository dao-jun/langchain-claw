package org.monarch.langchain.claw.core;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class SessionConcurrencyControl {

    private final Map<String, SessionLock> sessionLocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = java.util.concurrent.Executors.newCachedThreadPool();

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task) {
        SessionLock sessionLock = sessionLocks.computeIfAbsent(sessionId, ignored -> new SessionLock());
        sessionLock.holders.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            boolean acquired = false;
            try {
                sessionLock.semaphore.acquire();
                acquired = true;
                return task.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (acquired) {
                    sessionLock.semaphore.release();
                }
                if (sessionLock.holders.decrementAndGet() == 0 && sessionLock.semaphore.availablePermits() == 1) {
                    sessionLocks.remove(sessionId, sessionLock);
                }
            }
        }, executorService);
    }

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task, Duration timeout) {
        return executeInSession(sessionId, task).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
    }

    private static final class SessionLock {
        private final Semaphore semaphore = new Semaphore(1);
        private final AtomicInteger holders = new AtomicInteger();
    }
}
