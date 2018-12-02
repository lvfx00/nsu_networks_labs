package ru.nsu.fit.semenov.restchatutil.responses;

import org.jetbrains.annotations.Nullable;

public class SendMessageResponse {
    private final int id;
    private final String message;

    public static @Nullable SendMessageResponse checkForNulls(@Nullable SendMessageResponse sendMessageResponse) {
        if (sendMessageResponse == null ||
                sendMessageResponse.message == null ||
                sendMessageResponse.id < 1) {
            return null;
        }
        return sendMessageResponse;
    }

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
}
