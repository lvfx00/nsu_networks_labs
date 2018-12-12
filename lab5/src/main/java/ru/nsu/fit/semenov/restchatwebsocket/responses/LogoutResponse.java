package ru.nsu.fit.semenov.restchatwebsocket.responses;

import org.jetbrains.annotations.Nullable;

public class LogoutResponse {
    private final String message;

    public static @Nullable LogoutResponse checkForNulls(@Nullable LogoutResponse logoutResponse) {
        if (logoutResponse == null || logoutResponse.message == null) {
            return null;
        }
        return logoutResponse;
    }

    public LogoutResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
