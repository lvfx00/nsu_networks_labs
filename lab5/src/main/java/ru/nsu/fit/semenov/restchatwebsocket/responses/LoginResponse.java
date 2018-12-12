package ru.nsu.fit.semenov.restchatwebsocket.responses;

import org.jetbrains.annotations.Nullable;

public class LoginResponse {
    private final int id;
    private final String username;
    private final boolean online;
    private final String token;

    public static @Nullable LoginResponse checkForNulls(@Nullable LoginResponse loginResponse) {
        if (loginResponse == null ||
                loginResponse.id < 1 ||
                loginResponse.username == null ||
                loginResponse.token == null) {
            return null;
        }
        return loginResponse;
    }

    public LoginResponse(int id, String username, boolean online, String token) {
        this.id = id;
        this.username = username;
        this.online = online;
        this.token = token;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isOnline() {
        return online;
    }

    public String getToken() {
        return token;
    }
}
