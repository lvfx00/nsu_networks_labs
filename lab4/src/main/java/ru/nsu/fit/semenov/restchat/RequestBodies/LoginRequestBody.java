package ru.nsu.fit.semenov.restchat.RequestBodies;

import static com.google.common.base.Preconditions.checkNotNull;

public class LoginRequestBody {
    private final String username;

    public LoginRequestBody(String username) {
        this.username = checkNotNull(username);
    }

    public String getUsername() {
        return username;
    }
}
