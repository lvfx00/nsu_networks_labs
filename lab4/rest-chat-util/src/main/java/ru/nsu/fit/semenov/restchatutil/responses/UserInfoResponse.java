package ru.nsu.fit.semenov.restchatutil.responses;

import org.jetbrains.annotations.Nullable;

public class UserInfoResponse {
    private final int id;
    private final String username;
    private final String online;

    public static @Nullable UserInfoResponse checkForNulls(@Nullable UserInfoResponse userInfoResponse) {
        if (userInfoResponse == null ||
                userInfoResponse.username == null ||
                userInfoResponse.online == null ||
                userInfoResponse.id < 1) {
            return null;
        }
        return userInfoResponse;
    }

    public UserInfoResponse(int id, String username, String online) {
        this.id = id;
        this.username = username;
        this.online = online;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getOnline() {
        return online;
    }
}
