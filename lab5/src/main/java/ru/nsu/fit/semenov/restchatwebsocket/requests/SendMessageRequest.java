package ru.nsu.fit.semenov.restchatwebsocket.requests;

import org.jetbrains.annotations.Nullable;

public class SendMessageRequest {
    private final String message;

    public static @Nullable SendMessageRequest checkForNulls(@Nullable SendMessageRequest sendMessageRequest) {
        if (sendMessageRequest == null || sendMessageRequest.message == null) {
            return null;
        }
        return sendMessageRequest;
    }

    public SendMessageRequest(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

}
