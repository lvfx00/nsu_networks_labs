package ru.nsu.fit.semenov.restchat.user;

import com.sun.istack.internal.NotNull;

public class UserInfo {
    private final String username;
    private final int id;

    public UserInfo(@NotNull String username, int id) {
        if (id < 1) {
            throw new IllegalArgumentException("Id must be > 0");
        }

        this.username = username;
        this.id = id;
    }

    public @NotNull String getUsername() {
        return username;
    }

    public int getId() {
        return id;
    }
}
