package org.monarch.langchain.claw.core;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SessionConcurrencyControl {

    private final Map<String, Semaphore> sessionLocks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = java.util.concurrent.Executors.newCachedThreadPool();

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task) {
        Semaphore lock = sessionLocks.computeIfAbsent(sessionId, ignored -> new Semaphore(1));
        return CompletableFuture.supplyAsync(() -> {
            boolean acquired = false;
            try {
                lock.acquire();
                acquired = true;
                return task.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                if (acquired) {
                    lock.release();
                }
            }
        }, executorService);
    }

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task, Duration timeout) {
        return executeInSession(sessionId, task).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
