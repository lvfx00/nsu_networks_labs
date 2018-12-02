package ru.nsu.fit.semenov.restchatutil.requests;

import org.jetbrains.annotations.Nullable;

public class LoginRequest {
    private final String username;

    public static @Nullable LoginRequest checkForNulls(@Nullable LoginRequest loginRequest) {
        if (loginRequest == null || loginRequest.username == null) {
            return null;
        }
        return loginRequest;
    }

    public LoginRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

