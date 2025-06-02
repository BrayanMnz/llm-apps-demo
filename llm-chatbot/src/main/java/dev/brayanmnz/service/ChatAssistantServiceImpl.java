package dev.brayanmnz.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.List;

@Service
public class ChatAssistantServiceImpl implements ChatAssistantService {

    private final ChatClient.Builder builder;

    public ChatAssistantServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.builder = chatClientBuilder;
    }

    @Override
    public Flux<String> streamChatResponse(String prompt, List<Message> conversationHistory) {
        ChatClient chatClient = builder.build();
        return chatClient.prompt(prompt)
                .messages(conversationHistory)
                .stream()
                .content();
    }
}