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
        entity.setImportanceScore(Math.min(1.0d, 0.1d + content.length() / 200.0d));
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

    private Set<String> tokenize(String content) {
        return Arrays.stream(content.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
            .filter(token -> !token.isBlank())
            .collect(Collectors.toSet());
    }
}
