package org.monarch.langchain.claw.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(ZhipuAiProperties.class)
public class LlmModelConfiguration implements ChatLanguageModelFactory {

    private final ZhipuAiProperties zhipuAiProperties;
    private final Map<ModelKey, ChatLanguageModel> cache = new ConcurrentHashMap<>();

    public LlmModelConfiguration(ZhipuAiProperties zhipuAiProperties) {
        this.zhipuAiProperties = zhipuAiProperties;
    }

    @Override
    public ChatLanguageModel create(ModelSelection selection) {
        String provider = normalizedProvider(selection);
        ModelKey key = new ModelKey(
            provider,
            selection.getModelName(),
            selection.getTemperature(),
            selection.getMaxTokens());
        return cache.computeIfAbsent(key, ignored -> createModel(selection, provider));
    }

    private ChatLanguageModel createModel(ModelSelection selection, String provider) {
        return switch (provider) {
            case "zhipu", "zhipu-ai" -> buildZhipuModel(selection);
            case "rule-based" -> throw new IllegalArgumentException("rule-based provider does not create a ChatLanguageModel.");
            default -> throw new IllegalArgumentException("Unsupported chat model provider: " + selection.getProvider());
        };
    }

    private ChatLanguageModel buildZhipuModel(ModelSelection selection) {
        String apiKey = firstNonBlank(zhipuAiProperties.getApiKey(), System.getenv("ZHIPU_AI_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Zhipu AI API key is not configured. Set claw.llm.zhipu.api-key, ZHIPU_AI_API_KEY, or OPENAI_API_KEY.");
        }
        return ZhipuAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(zhipuAiProperties.getBaseUrl())
            .model(selection.getModelName())
            .temperature(selection.getTemperature())
            .maxToken(selection.getMaxTokens())
            .maxRetries(zhipuAiProperties.getMaxRetries())
            .logRequests(zhipuAiProperties.getLogRequests())
            .logResponses(zhipuAiProperties.getLogResponses())
            .callTimeout(zhipuAiProperties.getCallTimeout())
            .connectTimeout(zhipuAiProperties.getConnectTimeout())
            .readTimeout(zhipuAiProperties.getReadTimeout())
            .writeTimeout(zhipuAiProperties.getWriteTimeout())
            .build();
    }

    private String normalizedProvider(ModelSelection selection) {
        String provider = selection == null ? null : selection.getProvider();
        if (!StringUtils.hasText(provider)) {
            return "rule-based";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private record ModelKey(String provider, String modelName, Double temperature, Integer maxTokens) {

        private ModelKey {
            provider = Objects.requireNonNullElse(provider, "rule-based");
            modelName = Objects.requireNonNullElse(modelName, "");
        }
    }
}
