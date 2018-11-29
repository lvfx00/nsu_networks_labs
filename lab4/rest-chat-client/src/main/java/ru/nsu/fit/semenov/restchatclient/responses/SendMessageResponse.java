package ru.nsu.fit.semenov.restchatclient.responses;

import org.jetbrains.annotations.Nullable;

public class SendMessageResponse {
    private final String message;
    private final int id;

    public SendMessageResponse(String message, int id) {
        this.message = message;
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public int getId() {
        return id;
    }

    public static @Nullable SendMessageResponse checkNulls(@Nullable SendMessageResponse sendMessageResponse) {
        if (sendMessageResponse == null ||
                sendMessageResponse.message == null ||
                sendMessageResponse.id < 1) {
            return null;
        } else {
            return sendMessageResponse;
        }
    }
}
