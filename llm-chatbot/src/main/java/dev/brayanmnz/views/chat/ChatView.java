package dev.brayanmnz.views.chat;

import com.vaadin.collaborationengine.CollaborationMessageInput;
import com.vaadin.collaborationengine.CollaborationMessageList;
import com.vaadin.collaborationengine.MessageManager;
import com.vaadin.collaborationengine.UserInfo;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInputI18n;
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

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Asistente Financiero Inteligente 🤖 🇩🇴")
@Route("")
@Menu(order = 0, icon = LineAwesomeIconUrl.COMMENTS)
public class ChatView extends HorizontalLayout {

    transient Logger logger = LoggerFactory.getLogger(ChatView.class);
    transient ChatClient.Builder builder;
    private CollaborationMessageInput input;
    private final CollaborationMessageList messageList;
    private VerticalLayout chatContainer;


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

        public void resetUnread() {
            unread = 0;
            updateBadge();
        }

        public void incrementUnread() {
            unread++;
            updateBadge();
        }

        private void updateBadge() {
            unreadBadge.setText(unread + "");
            unreadBadge.setVisible(unread != 0);
        }

        public void setUnreadBadge(Span unreadBadge) {
            this.unreadBadge = unreadBadge;
            updateBadge();
        }

        public String getCollaborationTopic() {
            return "chat/" + name;
        }
    }

    private transient ChatInfo[] chats = new ChatInfo[]{new ChatInfo("general", 0)};
    private transient ChatInfo currentChat = chats[0];
    private Tabs tabs;

    public ChatView(ChatClient.Builder builder) {
        this.builder = builder;
        addClassNames("chat-view", Width.FULL, Display.FLEX, Flex.AUTO);
        setSpacing(false);

        // UserInfo is used by Collaboration Engine and is used to share details
        // of users to each other to able collaboration. Replace this with
        // information about the actual user that is logged, providing a user
        // identifier, and the user's real name. You can also provide the users
        // avatar by passing an url to the image as a third parameter, or by
        // configuring an `ImageProvider` to `avatarGroup`.
        UserInfo userInfo = new UserInfo(UUID.randomUUID().toString(), "Brayan Muñoz");

        tabs = new Tabs();
        for (ChatInfo chat : chats) {
            // Listen for new messages in each chat so we can update the
            // "unread" count
            MessageManager mm = new MessageManager(this, userInfo, chat.getCollaborationTopic());
            mm.setMessageHandler(context -> {
                if (currentChat != chat) {
                    chat.incrementUnread();
                }

                if (context.getMessage().getUser().getId().equals(userInfo.getId()) && currentChat == chat) {
                    String userMessageText = context.getMessage().getText();
                    respondAsAiAssistant(userMessageText, chat.getCollaborationTopic());
                }

            });

            tabs.add(createTab(chat));
        }
        tabs.setOrientation(Orientation.VERTICAL);
        tabs.addClassNames(Flex.GROW, Flex.SHRINK, Overflow.HIDDEN);

        // CollaborationMessageList displays messages that are in a
        // Collaboration Engine topic. You should give in the user details of
        // the current user using the component, and a topic Id. Topic id can be
        // any freeform string. In this template, we have used the format
        // "chat/#general".
        messageList = new CollaborationMessageList(userInfo, currentChat.getCollaborationTopic());
        messageList.setSizeFull();

        // `CollaborationMessageInput is a textfield and button, to be able to
        // submit new messages. To avoid having to set the same info into both
        // the message list and message input, the input takes in the list as an
        // constructor argument to get the information from there.
        this.input = new CollaborationMessageInput(messageList);
        input.setTooltipText("Escribe tu consulta financiera");
        input.setI18n(new MessageInputI18n().setMessage("Mensaje").setSend("Consulta"));
        input.setWidthFull();

        // Layouting
        this.chatContainer = new VerticalLayout();
        // Use Overflow.AUTO to allow chatContainer to scroll
        this.chatContainer.addClassNames(Flex.AUTO, LumoUtility.Overflow.AUTO, LumoUtility.Padding.NONE);
        this.chatContainer.setSpacing(false);


        this.chatContainer.add(messageList, this.input);
        expand(messageList);
        add(chatContainer);
        setSizeFull();


        // Change the topic id of the chat when a new tab is selected
        tabs.addSelectedChangeListener(event -> {
            currentChat = ((ChatTab) event.getSelectedTab()).getChatInfo();
            currentChat.resetUnread();
            messageList.setTopic(currentChat.getCollaborationTopic());
        });
    }

    /**
     * Generates a response as the AI Assistant based on the user's message and submits it.
     *
     * @param originalUserMessage The message text from the human user.
     * @param topicId             The collaboration topic ID where the AI should respond.
     */
    private void respondAsAiAssistant(String originalUserMessage, String topicId) {
        UserInfo AI_USER_INFO = new UserInfo(UUID.randomUUID().toString(), "AI Assistant 🤖", "https://png.pngtree.com/png-clipart/20210311/original/pngtree-cute-robot-mascot-logo-png-image_6023574.jpg");

        final UI currentUI = UI.getCurrent();
        if (currentUI == null) {
            logger.error("Cannot respond as AI: UI is not available.");
            return;
        }

        // Temporary Div for streaming content
        Div aiStreamingMessageWrapper = new Div();
        // Style like a message item: avatar + content bubble
        aiStreamingMessageWrapper.addClassNames(
                LumoUtility.Display.FLEX, LumoUtility.Gap.SMALL, LumoUtility.Padding.Horizontal.SMALL, LumoUtility.Padding.Vertical.XSMALL
        );

        int inputComponentIndex = chatContainer.indexOf(this.input);
        if (inputComponentIndex != -1) {
            chatContainer.addComponentAtIndex(inputComponentIndex, aiStreamingMessageWrapper);
        } else {
            chatContainer.add(aiStreamingMessageWrapper);
            logger.warn("Could not find input component in chatContainer to insert streaming message above it. Appending to end.");
        }

        scrollToBottomChatContainer(currentUI); // Scroll after user message is likely rendered

        Span avatarSpan = new Span("AI");
        avatarSpan.addClassNames(
                LumoUtility.FontSize.SMALL, LumoUtility.TextColor.PRIMARY,
                LumoUtility.Background.CONTRAST_10, LumoUtility.BorderRadius.LARGE,
                LumoUtility.Width.MEDIUM, LumoUtility.Height.MEDIUM,
                LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.JustifyContent.CENTER,
                LumoUtility.Flex.SHRINK_NONE
        );

        Div messageBubble = new Div();
        messageBubble.addClassNames(
                LumoUtility.Background.CONTRAST_5, LumoUtility.BorderRadius.MEDIUM,
                LumoUtility.Padding.SMALL, "ai-streaming-bubble" // Custom class for further styling if needed
        );
        Span messageTextSpan = new Span("..."); // Initial placeholder text
        messageBubble.add(messageTextSpan);
        aiStreamingMessageWrapper.add(avatarSpan, messageBubble);

        StringBuilder fullResponse = new StringBuilder();
        ChatClient chatClient = builder.build();

        chatClient.prompt()
                .user(originalUserMessage)
                .stream()
                .content() // Returns Flux<String>
                // .publishOn(Schedulers.boundedElastic()) // Optional: ensure non-UI thread for subscription if Spring AI doesn't guarantee
                .doOnNext(chunk -> {
                    if (currentUI.isAttached()) {
                        currentUI.access(() -> {
                            // Check attachment again inside access, as UI/component might detach
                            if (currentUI.isAttached() && aiStreamingMessageWrapper.isAttached()) {
                                if (fullResponse.isEmpty() && chunk.isBlank()) {
                                    // Skip leading blank chunks if message is empty
                                    return;
                                }
                                fullResponse.append(chunk);
                                messageTextSpan.setText(fullResponse.toString());
                                scrollToBottomChatContainer(currentUI);
                            }
                        });
                    }
                })
                .doOnComplete(() -> {
                    if (currentUI.isAttached()) {
                        currentUI.access(() -> {
                            if (currentUI.isAttached()) {
                                if (aiStreamingMessageWrapper.isAttached()) {
                                    chatContainer.remove(aiStreamingMessageWrapper);
                                }
                                if (!fullResponse.toString().isBlank()) {
                                    MessageManager aiMessageManager = new MessageManager(this, AI_USER_INFO, topicId);
                                    aiMessageManager.submit(fullResponse.toString().trim());
                                    logger.info("AI Assistant (streamed) submitted to topic '{}'", topicId);
                                } else {
                                    logger.info("AI Assistant (streamed) produced empty response for topic '{}'", topicId);
                                }
                                scrollToBottomChatContainer(currentUI); // Scroll after final message is added
                            } else {
                                handleUIDetached("stream completion", null);
                            }
                        });
                    } else {
                        handleUINotAvailable("stream completion", null);
                    }
                })
                .doOnError(error -> {
                    logger.error("Error streaming AI response for topic '{}': {}", topicId, error.getMessage(), error);
                    if (currentUI.isAttached()) {
                        currentUI.access(() -> {
                            if (currentUI.isAttached()) {
                                if (aiStreamingMessageWrapper.isAttached()) {
                                    chatContainer.remove(aiStreamingMessageWrapper);
                                }
                                UserInfo systemInfo = new UserInfo("system-error-" + UUID.randomUUID(), "System Error");
                                MessageManager errorManager = new MessageManager(this, systemInfo, topicId);
                                errorManager.submit("Lo siento, ocurrió un error al intentar responder: " + error.getMessage());
                                scrollToBottomChatContainer(currentUI);
                            } else {
                                handleUIDetached("stream error", error);
                            }
                        });
                    } else {
                        handleUINotAvailable("stream error", error);
                    }
                })
                .subscribe(
                        content -> { /* Handled in doOnNext */ },
                        error -> { /* Handled in doOnError */ },
                        () -> { /* Handled in doOnComplete */ }
                );
    }

    private ChatTab createTab(ChatInfo chat) {
        ChatTab tab = new ChatTab(chat);
        tab.addClassNames(JustifyContent.BETWEEN);

        Span badge = new Span();
        chat.setUnreadBadge(badge);
        badge.getElement().getThemeList().add("badge small contrast");
        tab.add(new Span("#" + chat.name), badge);

        return tab;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        Page page = attachEvent.getUI().getPage();
        page.retrieveExtendedClientDetails(details -> setMobile(details.getWindowInnerWidth() < 740));
        page.addBrowserWindowResizeListener(e -> setMobile(e.getWidth() < 740));
    }

    private void setMobile(boolean mobile) {
        tabs.setOrientation(mobile ? Orientation.HORIZONTAL : Orientation.VERTICAL);
    }

    // Helper method for scrolling the main chat container
    private void scrollToBottomChatContainer(UI ui) {
        if (ui != null && ui.isAttached() && chatContainer != null && chatContainer.isAttached()) {
            ui.access(() -> { // Ensure UI access
                if (chatContainer.isAttached()) { // Double check inside access
                    chatContainer.getElement().executeJs("this.scrollTop = this.scrollHeight");
                }
            });
        }
    }

    // Helper methods for logging UI detachment/unavailability and cleaning up
    private void cleanupLingeringUI(String logContext, Throwable error) {
        String errorMessage = error != null ? error.getMessage() : "N/A";
        logger.warn("UI state issue during {}. Error: {}. Attempting cleanup.", logContext, errorMessage);
        // If aiStreamingMessageWrapper were a field and could be accessed here,
        // you might try to remove it too, but it's local to respondAsAiAssistant.
    }

    private void handleUIDetached(String context, Throwable error) {
        cleanupLingeringUI("UI detached during " + context, error);
    }

    private void handleUINotAvailable(String context, Throwable error) {
        cleanupLingeringUI("UI not available for " + context, error);
    }

}
