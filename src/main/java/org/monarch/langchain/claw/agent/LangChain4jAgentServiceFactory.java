package org.monarch.langchain.claw.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.monarch.langchain.claw.config.ChatLanguageModelFactory;
import org.monarch.langchain.claw.config.ModelSelection;
import org.monarch.langchain.claw.tool.LangChain4jExecutionTools;
import org.springframework.stereotype.Service;

@Service
public class LangChain4jAgentServiceFactory {

    private final ChatLanguageModelFactory chatLanguageModelFactory;
    private final LangChain4jExecutionTools langChain4jExecutionTools;
    private final Map<ServiceCacheKey, Object> cache = new ConcurrentHashMap<>();

    public LangChain4jAgentServiceFactory(ChatLanguageModelFactory chatLanguageModelFactory,
                                          LangChain4jExecutionTools langChain4jExecutionTools) {
        this.chatLanguageModelFactory = chatLanguageModelFactory;
        this.langChain4jExecutionTools = langChain4jExecutionTools;
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

    public ToolCallingExecutorAiService toolCallingExecutorService(ModelSelection selection) {
        return getOrCreate(selection, ToolCallingExecutorAiService.class);
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
        AiServices<T> builder = AiServices.builder(serviceType)
            .chatLanguageModel(chatLanguageModel);
        if (ToolCallingExecutorAiService.class.equals(serviceType)) {
            builder.tools(langChain4jExecutionTools);
        }
        return builder.build();
    }

    private record ServiceCacheKey(String serviceType,
                                   String provider,
                                   String modelName,
                                   Double temperature,
                                   Integer maxTokens) {
    }
}
