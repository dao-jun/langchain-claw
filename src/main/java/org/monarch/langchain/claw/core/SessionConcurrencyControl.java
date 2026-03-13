package org.monarch.langchain.claw.core;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SessionConcurrencyControl {

    private final Map<String, Object> sessionMonitors = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> sessionQueues = new ConcurrentHashMap<>();
    private final ExecutorService executorService = java.util.concurrent.Executors.newCachedThreadPool();

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task) {
        Object monitor = sessionMonitors.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (monitor) {
            CompletableFuture<?> previous = sessionQueues.getOrDefault(sessionId, CompletableFuture.completedFuture(null));
            CompletableFuture<T> next = previous.handle((ignored, throwable) -> null)
                .thenApplyAsync(ignored -> call(task), executorService);
            sessionQueues.put(sessionId, next);
            next.whenComplete((ignored, throwable) -> cleanup(sessionId, monitor, next));
            return next;
        }
    }

    public <T> CompletableFuture<T> executeInSession(String sessionId, Callable<T> task, Duration timeout) {
        return executeInSession(sessionId, task).orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private void cleanup(String sessionId, Object monitor, CompletableFuture<?> currentFuture) {
        synchronized (monitor) {
            if (sessionQueues.get(sessionId) == currentFuture) {
                sessionQueues.remove(sessionId);
                sessionMonitors.remove(sessionId, monitor);
            }
        }
    }
}
