package ru.nsu.fit.semenov.restchat.user;

import com.sun.istack.internal.NotNull;

import java.time.Instant;
import java.util.UUID;

public class SessionInfo {
    private final UUID sessionToken;
    private Instant lastActivity;

    public SessionInfo(@NotNull UUID sessionToken, @NotNull Instant lastActivity) {
        this.sessionToken = sessionToken;
        this.lastActivity = lastActivity;
    }

    public @NotNull UUID getSessionToken() {
        return sessionToken;
    }

    public @NotNull Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(@NotNull Instant lastActivity) {
        this.lastActivity = lastActivity;
    }
}
