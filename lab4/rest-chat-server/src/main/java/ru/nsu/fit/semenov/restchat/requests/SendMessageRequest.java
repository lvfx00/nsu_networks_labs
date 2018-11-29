package ru.nsu.fit.semenov.restchat.requests;

public class SendMessageRequest {
    private final String message;

    public String getMessage() {
        return message;
    }

    public SendMessageRequest(String message) {
        this.message = message;
    }
}
