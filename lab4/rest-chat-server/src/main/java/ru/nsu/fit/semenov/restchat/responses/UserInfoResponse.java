package ru.nsu.fit.semenov.restchat.responses;

public class UserInfoResponse {
    public UserInfoResponse(int id, String username, boolean online) {
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

    public boolean isOnline() {
        return online;
    }

    private final int id;
    private final String username;
    private final boolean online;
}
