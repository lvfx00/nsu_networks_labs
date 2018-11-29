package ru.nsu.fit.semenov.restchat.user;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class UserInfo {
    private final String username;
    private final int id;

    public UserInfo(@NotNull String username, int id) {
        this.username = checkNotNull(username);
        checkArgument(id > 0);
        this.id = id;
    }

    public @NotNull String getUsername() {
        return username;
    }

    public int getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInfo)) return false;
        UserInfo userInfo = (UserInfo) o;
        return id == userInfo.id &&
                Objects.equals(username, userInfo.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, id);
    }
}
