package ru.nsu.fit.semenov.restchatutil.responses;

import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatutil.Message;

import java.util.Collections;
import java.util.List;

public class MessagesListResponse {
    private final List<Message> messages;

    public static @Nullable MessagesListResponse checkForNulls(@Nullable MessagesListResponse messagesListResponse) {
        if (messagesListResponse == null || messagesListResponse.messages == null) {
            return null;
        }
        return messagesListResponse;
    }

    public MessagesListResponse(List<Message> messages) {
        this.messages = messages;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }
}
