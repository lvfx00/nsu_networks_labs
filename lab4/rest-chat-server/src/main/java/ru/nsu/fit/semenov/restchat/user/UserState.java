package ru.nsu.fit.semenov.restchat.user;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

public class UserState {
    private UserOnlineState onlineState;

    public @NotNull UserOnlineState getOnlineState() {
        return onlineState;
    }

    public void setOnlineState(@NotNull UserOnlineState onlineState) {
        this.onlineState = checkNotNull(onlineState);
    }

    public UserState(@NotNull UserOnlineState onlineState) {
        this.onlineState = onlineState;
    }
}
