package ru.nsu.fit.semenov.restchatwebsocket.client;

import org.jetbrains.annotations.NotNull;
import ru.nsu.fit.semenov.restchatwebsocket.responses.UserInfoResponse;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class UserInfo {
    private final int id;
    private final String username;
    private String online;

    public UserInfo(int id, @NotNull String username, @NotNull String online) {
        checkArgument(id > 0);
        this.id = id;
        this.username = checkNotNull(username);
        this.online = checkNotNull(online);
    }

    public UserInfo(@NotNull UserInfoResponse userInfoResponse) {
        id = checkNotNull(userInfoResponse).getId();
        username = userInfoResponse.getUsername();
        online = userInfoResponse.getOnline();
    }

    public int getId() {
        return id;
    }

    public @NotNull String getUsername() {
        return username;
    }

    public @NotNull String getOnline() {
        return online;
    }

    public void setOnline(@NotNull String online) {
        this.online = checkNotNull(online);
    }
}
