package org.monarch.langchain.claw.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class SessionConcurrencyControlTest {

    private final SessionConcurrencyControl control = new SessionConcurrencyControl();

    @Test
    void shouldSerializeTasksForSameSession() throws ExecutionException, InterruptedException {
        List<String> order = new CopyOnWriteArrayList<>();

        var first = control.executeInSession("session-1", () -> {
            order.add("first-start");
            Thread.sleep(100);
            order.add("first-end");
            return "first";
        }, Duration.ofSeconds(5));

        var second = control.executeInSession("session-1", () -> {
            order.add("second-start");
            order.add("second-end");
            return "second";
        }, Duration.ofSeconds(5));

        assertThat(first.get()).isEqualTo("first");
        assertThat(second.get()).isEqualTo("second");
        assertThat(order).containsExactly("first-start", "first-end", "second-start", "second-end");
    }
}
