package ru.nsu.fit.semenov.restchatwebsocket.server.user;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkNotNull;

public class UserState {
    private UserOnlineState onlineState;

    public UserState(UserOnlineState onlineState, boolean throughWebsocket) {
        this.onlineState = onlineState;
    }

    public @NotNull UserOnlineState getOnlineState() {
        return onlineState;
    }

    public void setOnlineState(@NotNull UserOnlineState onlineState) {
        this.onlineState = checkNotNull(onlineState);
    }
}
