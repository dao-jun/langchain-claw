package org.monarch.langchain.claw.memory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersistentMemoryManager implements MemoryManager {

    private static final double BASE_IMPORTANCE = 0.1d;
    private static final double IMPORTANCE_DIVISOR = 200.0d;
    private static final double MAX_IMPORTANCE = 1.0d;

    private final LongTermMemoryRepository repository;

    public PersistentMemoryManager(LongTermMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void storeConversation(String userId, String sessionId, String content) {
        LongTermMemoryEntity entity = new LongTermMemoryEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setContent(content);
        entity.setKeywords(extractKeywords(content));
        entity.setImportanceScore(heuristicImportanceScore(content));
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemorySnippet> recall(String userId, String message, int limit) {
        Set<String> queryTerms = tokenize(message);
        return repository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(memory -> new MemorySnippet(
                memory.getContent(),
                score(queryTerms, tokenize(memory.getKeywords())) * memory.getImportanceScore(),
                memory.getCreatedAt()))
            .filter(snippet -> snippet.score() > 0)
            .sorted(Comparator.comparingDouble(MemorySnippet::score).reversed())
            .limit(limit)
            .toList();
    }

    private double score(Set<String> queryTerms, Set<String> memoryTerms) {
        if (queryTerms.isEmpty() || memoryTerms.isEmpty()) {
            return 0;
        }
        long matches = queryTerms.stream().filter(memoryTerms::contains).count();
        return (double) matches / (double) queryTerms.size();
    }

    private String extractKeywords(String content) {
        return String.join(",", tokenize(content));
    }

    /**
     * A lightweight heuristic until semantic scoring is introduced: longer persisted exchanges tend
     * to contain more recoverable context, so they receive a slightly higher recall weight.
     */
    private double heuristicImportanceScore(String content) {
        return Math.min(MAX_IMPORTANCE, BASE_IMPORTANCE + content.length() / IMPORTANCE_DIVISOR);
    }

    private Set<String> tokenize(String content) {
        // Keep ASCII letters/digits plus the common Chinese block so memory recall works for both English and Chinese prompts.
        return Arrays.stream(content.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
            .filter(token -> !token.isBlank())
            .collect(Collectors.toSet());
    }
}
