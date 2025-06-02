package dev.brayanmnz.views.chat;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputI18n;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.Tabs.Orientation;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.vaadin.flow.theme.lumo.LumoUtility.Display;
import com.vaadin.flow.theme.lumo.LumoUtility.Flex;
import com.vaadin.flow.theme.lumo.LumoUtility.JustifyContent;
import com.vaadin.flow.theme.lumo.LumoUtility.Overflow;
import com.vaadin.flow.theme.lumo.LumoUtility.Width;
import com.vaadin.collaborationengine.UserInfo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.brayanmnz.service.ChatAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Asistente Financiero Inteligente 🤖 🇩🇴")
@Route("")
@Menu(order = 0, icon = LineAwesomeIconUrl.COMMENTS)
public class ChatView extends HorizontalLayout {

    transient Logger logger = LoggerFactory.getLogger(ChatView.class);

    private MessageInput input;
    private MessageList messageList;
    private VerticalLayout chatContainer;

    private UserInfo humanUserInfo;
    private static final UserInfo AI_USER_INFO = new UserInfo(
            "ai-assistant-" + UUID.randomUUID().toString(), // Unique ID for AI
            "AI Assistant 🤖",
            "https://png.pngtree.com/png-clipart/20210311/original/pngtree-cute-robot-mascot-logo-png-image_6023574.jpg"
    );

    private transient Map<String, List<MessageListItem>> chatHistories;
    private final ChatAssistantService chatAssistantService;

    public static class ChatTab extends Tab {
        private final transient ChatInfo chatInfo;

        public ChatTab(ChatInfo chatInfo) {
            this.chatInfo = chatInfo;
        }

        public ChatInfo getChatInfo() {
            return chatInfo;
        }
    }

    public static class ChatInfo {
        private String name;
        private int unread;
        private Span unreadBadge;

        private ChatInfo(String name, int unread) {
            this.name = name;
            this.unread = unread;
        }

        public String getName() {
            return name;
        }

        public void resetUnread() {
            unread = 0;
            updateBadge();
        }

        private void updateBadge() {
            if (unreadBadge != null) {
                unreadBadge.setText(unread > 0 ? String.valueOf(unread) : "");
                unreadBadge.setVisible(unread != 0);
            }
        }

        public void setUnreadBadge(Span unreadBadge) {
            this.unreadBadge = unreadBadge;
            updateBadge();
        }

    }

    private transient ChatInfo[] chats = new ChatInfo[]{new ChatInfo("general", 0)};
    private transient ChatInfo currentChat = chats[0];
    private Tabs tabs;

    public ChatView(ChatAssistantService chatAssistantService) {
        this.chatAssistantService = chatAssistantService;
        addClassNames("chat-view", Width.FULL, Display.FLEX, Flex.AUTO);
        setSpacing(false);

        humanUserInfo = new UserInfo(UUID.randomUUID().toString(), "Brayan Muñoz");

        tabs = new Tabs();
        chatHistories = new HashMap<>();

        for (ChatInfo chat : chats) {
            chatHistories.put(chat.getName(), new ArrayList<>());
            tabs.add(createTab(chat));
        }
        tabs.setOrientation(Orientation.VERTICAL);
        tabs.addClassNames(Flex.GROW_NONE, Flex.SHRINK_NONE, Overflow.HIDDEN, LumoUtility.Width.XSMALL);

        messageList = new MessageList();
        messageList.setMarkdown(true);
        messageList.setSizeFull();

        input = new MessageInput();
        input.setTooltipText("Escribe tu consulta financiera");
        input.setI18n(new MessageInputI18n().setMessage("Mensaje").setSend("Consulta"));
        input.setWidthFull();

        input.addSubmitListener(event -> {
            String userMessageText = event.getValue();
            if (userMessageText == null || userMessageText.isBlank()) {
                return;
            }

            MessageListItem userItem = createMessageListItem(userMessageText, humanUserInfo, false);
            logger.info("Created user message item: '{}'", userItem.getText());

            final UI currentUI = UI.getCurrent();
            if (currentUI == null || !currentUI.isAttached()) {
                logger.error("UI not available or attached when submitting user message.");
                return;
            }

            currentUI.access(() -> {
                if (messageList != null && messageList.isAttached()) {
                    List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                    currentDisplayItems.add(userItem);
                    messageList.setItems(currentDisplayItems);

                    logger.info("User message added to messageList. Total items now: {}. Last item: '{}'",
                            messageList.getItems().size(),
                            messageList.getItems().isEmpty() ? "N/A" : messageList.getItems().getLast().getText());

                    if (currentChat != null && currentChat.getName() != null && chatHistories != null) {
                        chatHistories.computeIfAbsent(currentChat.getName(), k -> new ArrayList<>()).add(userItem);
                        logger.info("User item added to history for chat: {}. History size for this chat: {}",
                                currentChat.getName(),
                                chatHistories.get(currentChat.getName()).size());
                    } else {
                        logger.warn("Could not add user item to history. currentChat, its name, or chatHistories might be null/problematic.");
                    }

                    scrollToBottomChatContainer(currentUI);
                } else {
                    logger.error("MessageList is null or not attached when trying to add user message.");
                }
            });

            respondAsAiAssistant(userMessageText, currentChat.getName());
        });

        chatContainer = new VerticalLayout();
        chatContainer.addClassNames(Flex.AUTO, LumoUtility.Overflow.AUTO, LumoUtility.Padding.NONE);
        chatContainer.setSpacing(false);
        chatContainer.setHeightFull();

        chatContainer.add(messageList, input);
        chatContainer.expand(messageList);

        add(tabs, chatContainer);
        expand(chatContainer);
        setSizeFull();

        messageList.setItems(chatHistories.get(currentChat.getName()));

        tabs.addSelectedChangeListener(event -> {
            currentChat = ((ChatTab) event.getSelectedTab()).getChatInfo();
            currentChat.resetUnread();
            messageList.setItems(chatHistories.getOrDefault(currentChat.getName(), new ArrayList<>()));
            scrollToBottomChatContainer(UI.getCurrent());
        });
    }

    private List<Message> convertToSpringAIMessages(List<MessageListItem> fullUiHistoryIncludingLatestUser) {
        List<Message> aiMessages = new ArrayList<>();
        for (MessageListItem uiItem : fullUiHistoryIncludingLatestUser) {
            String messageText = uiItem.getText();
            if (uiItem.getUserColorIndex() == 1) {
                aiMessages.add(new UserMessage(messageText));
            } else if (uiItem.getUserColorIndex() == 2 && (!"...".equals(messageText) && !messageText.isBlank())) {
                aiMessages.add(new AssistantMessage(messageText));
            }
        }
        return aiMessages;
    }

    private MessageListItem createMessageListItem(String text, UserInfo user, boolean isAssistant) {
        MessageListItem item = new MessageListItem(text, Instant.now(), user.getName());
        if (user.getImage() != null && !user.getImage().isEmpty()) {
            item.setUserImage(user.getImage());
        } else {
            String name = user.getName();
            if (name != null && !name.isEmpty()) {
                String[] parts = name.split("\\s+");
                if (parts.length > 1 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    item.setUserAbbreviation(String.valueOf(parts[0].charAt(0)) + parts[1].charAt(0));
                } else {
                    item.setUserAbbreviation(String.valueOf(name.charAt(0)));
                }
            } else {
                item.setUserAbbreviation("?");
            }
        }
        item.setUserColorIndex(isAssistant ? 2 : 1);
        return item;
    }


    private void respondAsAiAssistant(String originalUserMessageText, String chatName) {
        final UI currentUI = UI.getCurrent();
        if (currentUI == null) {
            logger.error("Cannot respond as AI: UI is not available for chat '{}'", chatName);
            return;
        }

        MessageListItem aiMessageItem = createMessageListItem("...", AI_USER_INFO, true);
        addMessageToUI(currentUI, aiMessageItem);

        List<MessageListItem> currentChatUIHistory = chatHistories.getOrDefault(chatName, new ArrayList<>());
        List<Message> conversationHistoryForAI = convertToSpringAIMessages(new ArrayList<>(currentChatUIHistory));

        logger.info("Sending {} messages to AI as context for chat '{}'.",
                conversationHistoryForAI.size(), chatName);

        StringBuilder fullResponse = new StringBuilder();

        chatAssistantService.streamChatResponse(originalUserMessageText, conversationHistoryForAI)
                .doOnNext(chunk -> handleStreamingChunk(currentUI, aiMessageItem, fullResponse, chunk))
                .doOnComplete(() -> handleStreamCompletion(currentUI, aiMessageItem, fullResponse, chatName))
                .doOnError(error -> handleStreamError(currentUI, aiMessageItem, chatName, error))
                .subscribe(
                        content -> { /* Handled in doOnNext */ },
                        error -> { /* Handled in doOnError */ },
                        () -> { /* Handled in doOnComplete */ }
                );
    }

    private void addMessageToUI(UI currentUI, MessageListItem item) {
        currentUI.access(() -> {
            if (messageList.isAttached()) {
                List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                currentDisplayItems.add(item);
                messageList.setItems(currentDisplayItems);
                scrollToBottomChatContainer(currentUI);
            } else {
                logger.warn("MessageList not attached when trying to add item: {}", item.getText());
            }
        });
    }

    private void handleStreamingChunk(UI currentUI, MessageListItem aiMessageItem, StringBuilder fullResponse, String chunk) {
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached() && aiMessageItem != null) {
                    if (fullResponse.isEmpty() && chunk.isBlank() && aiMessageItem.getText().equals("...")) {
                        return; // Skip leading blank chunks if placeholder is still "..."
                    }
                    // Always set the full text as MessageListItem doesn't have appendText
                    if (aiMessageItem.getText().equals("...")) { // First actual chunk
                        fullResponse.append(chunk);
                        aiMessageItem.setText(fullResponse.toString());
                    } else { // Subsequent chunks
                        fullResponse.append(chunk);
                        aiMessageItem.setText(fullResponse.toString());
                    }
                    scrollToBottomChatContainer(currentUI);
                }
            });
        }
    }

    private void handleStreamCompletion(UI currentUI, MessageListItem aiMessageItem, StringBuilder fullResponse, String chatName) {
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached() && aiMessageItem != null) {
                    String finalResponseText = fullResponse.toString().trim();
                    if (finalResponseText.isBlank() && aiMessageItem.getText().equals("...")) {
                        aiMessageItem.setText("(AI no generó respuesta)");
                        logger.info("AI Assistant (streamed) produced empty response for chat '{}'", chatName);
                    } else if (!finalResponseText.isBlank()) {
                        aiMessageItem.setText(finalResponseText);
                    }

                    List<MessageListItem> historyList = chatHistories.computeIfAbsent(chatName, k -> new ArrayList<>());
                    if (!historyList.contains(aiMessageItem)) {
                        // This ensures the updated aiMessageItem (by reference) is in history
                        historyList.add(aiMessageItem);
                    }
                    logger.info("AI Assistant (streamed) response completed for chat '{}'. History size: {}", chatName, historyList.size());
                    scrollToBottomChatContainer(currentUI);
                } else {
                    handleUIDetached("stream completion for " + chatName, null);
                }
            });
        } else {
            handleUINotAvailable("stream completion for " + chatName, null);
        }
    }

    private void handleStreamError(UI currentUI, MessageListItem aiMessageItem, String chatName, Throwable error) {
        logger.error("Error streaming AI response for chat '{}': {}", chatName, error.getMessage(), error);
        if (currentUI.isAttached()) {
            currentUI.access(() -> {
                if (messageList.isAttached()) {
                    List<MessageListItem> currentDisplayItems = new ArrayList<>(messageList.getItems());
                    if (aiMessageItem != null && currentDisplayItems.contains(aiMessageItem)) {
                        currentDisplayItems.remove(aiMessageItem);
                        chatHistories.getOrDefault(chatName, new ArrayList<>()).remove(aiMessageItem);
                    }

                    MessageListItem errorItem = createMessageListItem(
                            "Lo siento, ocurrió un error al intentar responder: " + error.getMessage(),
                            new UserInfo("system-error", "System Error"), true);
                    errorItem.setUserColorIndex(3);

                    currentDisplayItems.add(errorItem);
                    messageList.setItems(currentDisplayItems);

                    chatHistories.computeIfAbsent(chatName, k -> new ArrayList<>()).add(errorItem);
                    scrollToBottomChatContainer(currentUI);
                } else {
                    handleUIDetached("stream error for " + chatName, error);
                }
            });
        } else {
            handleUINotAvailable("stream error for " + chatName, error);
        }
    }

    private ChatTab createTab(ChatInfo chat) {
        ChatTab tab = new ChatTab(chat);
        tab.addClassNames(JustifyContent.BETWEEN, LumoUtility.Padding.SMALL);

        Span badge = new Span();
        chat.setUnreadBadge(badge);
        badge.getElement().getThemeList().add("badge small contrast");
        badge.getStyle().set("margin-inline-start", "0.5em");

        tab.add(badge);
        return tab;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        Page page = attachEvent.getUI().getPage();
        page.retrieveExtendedClientDetails(details -> {
            if (details != null) setMobile(details.getWindowInnerWidth() < 740);
        });
        page.addBrowserWindowResizeListener(e -> setMobile(e.getWidth() < 740));
    }

    private void setMobile(boolean mobile) {
        tabs.setOrientation(mobile ? Orientation.HORIZONTAL : Orientation.VERTICAL);
        if (mobile) {
            tabs.removeClassName(LumoUtility.Width.XSMALL);
            tabs.addClassName(LumoUtility.Width.FULL);
        } else {
            tabs.removeClassName(LumoUtility.Width.FULL);
            tabs.addClassName(LumoUtility.Width.XSMALL);
        }
    }

    private void scrollToBottomChatContainer(UI ui) {
        if (ui != null && ui.isAttached() && chatContainer != null && chatContainer.isAttached()) {
            ui.access(() -> {
                if (chatContainer.isAttached()) {
                    chatContainer.getElement().executeJs("this.scrollTop = this.scrollHeight");
                }
            });
        }
    }

    private void cleanupLingeringUI(String logContext, Throwable error) {
        String errorMessage = error != null ? error.getMessage() : "N/A";
        logger.warn("UI state issue during {}. Error: {}. No specific UI elements to clean in this version.", logContext, errorMessage);
    }

    private void handleUIDetached(String context, Throwable error) {
        cleanupLingeringUI("UI detached during " + context, error);
    }

    private void handleUINotAvailable(String context, Throwable error) {
        cleanupLingeringUI("UI not available for " + context, error);
    }
}