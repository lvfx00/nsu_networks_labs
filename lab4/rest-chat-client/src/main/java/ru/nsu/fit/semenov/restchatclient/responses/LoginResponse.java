package ru.nsu.fit.semenov.restchatclient.responses;

import org.jetbrains.annotations.Nullable;

public class LoginResponse {
    private final int id;
    private final String username;
    private final boolean online;
    private final String token;

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

    public static @Nullable LoginResponse checkNulls(@Nullable LoginResponse loginResponse) {
        if (loginResponse == null ||
                loginResponse.getUsername() == null ||
                loginResponse.getToken() == null ||
                loginResponse.id < 1) {
            return null;
        } else {
            return loginResponse;
        }
    }
}
