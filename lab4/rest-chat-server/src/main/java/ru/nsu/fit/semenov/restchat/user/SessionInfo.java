package ru.nsu.fit.semenov.restchat.user;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO split session info to token and last activity
public class SessionInfo {
    private final Token sessionToken;
    private Instant lastActivity;

    public SessionInfo(@NotNull Token sessionToken, @NotNull Instant lastActivity) {
        this.sessionToken = checkNotNull(sessionToken);
        this.lastActivity = checkNotNull(lastActivity);
    }

    public @NotNull Token getSessionToken() {
        return sessionToken;
    }

    public @NotNull Instant getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(@NotNull Instant lastActivity) {
        this.lastActivity = checkNotNull(lastActivity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SessionInfo)) return false;
        SessionInfo that = (SessionInfo) o;
        return Objects.equals(sessionToken, that.sessionToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionToken);
    }
}
