package ru.nsu.fit.semenov.restchat.responses;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

public class LogoutResponse {
    private final String message;

    public LogoutResponse(@NotNull String message) {
        this.message = checkNotNull(message);
    }

    public @NotNull String getMessage() {
        return message;
    }
}
