package ru.nsu.fit.semenov.restchat.user;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public class Token {
    private final UUID token;

    public Token(@NotNull UUID token) {
        this.token = checkNotNull(token);
    }

    public Token(@NotNull String token) {
        this.token = UUID.fromString(checkNotNull(token));
    }

    @Override
    public String toString() {
        return token.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Token)) return false;
        Token token1 = (Token) o;
        return Objects.equals(token, token1.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }
}
