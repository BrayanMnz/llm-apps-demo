package dev.brayanmnz.service;

import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatAssistantService {
    /**
     * Streams a chat response from the AI assistant.
     *
     * @param conversationHistory The complete conversation history, including the latest user message
     *                            that the AI needs to respond to.
     * @return A Flux emitting chunks of the AI's response.
     */
    Flux<String> streamChatResponse(String prompt, List<Message> conversationHistory);
}
