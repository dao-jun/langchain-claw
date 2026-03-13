package org.monarch.langchain.claw.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.monarch.langchain.claw.config.ChatLanguageModelFactory;
import org.monarch.langchain.claw.config.ModelSelection;
import org.springframework.stereotype.Service;

@Service
public class LangChain4jAgentServiceFactory {

    private final ChatLanguageModelFactory chatLanguageModelFactory;
    private final Map<ServiceCacheKey, Object> cache = new ConcurrentHashMap<>();

    public LangChain4jAgentServiceFactory(ChatLanguageModelFactory chatLanguageModelFactory) {
        this.chatLanguageModelFactory = chatLanguageModelFactory;
    }

    public PlanGenerationAiService planService(ModelSelection selection) {
        return getOrCreate(selection, PlanGenerationAiService.class);
    }

    public ReviewAiService reviewService(ModelSelection selection) {
        return getOrCreate(selection, ReviewAiService.class);
    }

    public ConversationAiService conversationService(ModelSelection selection) {
        return getOrCreate(selection, ConversationAiService.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreate(ModelSelection selection, Class<T> serviceType) {
        ServiceCacheKey cacheKey = new ServiceCacheKey(
            serviceType.getName(),
            selection == null ? null : selection.getProvider(),
            selection == null ? null : selection.getModelName(),
            selection == null ? null : selection.getTemperature(),
            selection == null ? null : selection.getMaxTokens());
        return (T) cache.computeIfAbsent(cacheKey, ignored -> buildService(serviceType, selection));
    }

    private <T> T buildService(Class<T> serviceType, ModelSelection selection) {
        ChatLanguageModel chatLanguageModel = chatLanguageModelFactory.create(selection);
        return AiServices.builder(serviceType)
            .chatLanguageModel(chatLanguageModel)
            .build();
    }

    private record ServiceCacheKey(String serviceType,
                                   String provider,
                                   String modelName,
                                   Double temperature,
                                   Integer maxTokens) {
    }
}
