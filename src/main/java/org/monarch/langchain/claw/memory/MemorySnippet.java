package org.monarch.langchain.claw.memory;

import java.time.Instant;

public record MemorySnippet(String content, double score, Instant createdAt) {
}
