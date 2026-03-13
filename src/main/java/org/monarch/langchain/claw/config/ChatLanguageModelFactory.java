package org.monarch.langchain.claw.config;

import dev.langchain4j.model.chat.ChatLanguageModel;

public interface ChatLanguageModelFactory {

    ChatLanguageModel create(ModelSelection selection);
}
