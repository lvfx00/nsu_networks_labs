package ru.nsu.fit.semenov.restchatclient.responses;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class LogoutResponse {
    private final String message;

    public LogoutResponse(@NotNull String message) {
        this.message = checkNotNull(message);
    }

    public @NotNull String getMessage() {
        return message;
    }

    public static @Nullable LogoutResponse checkNulls(@Nullable LogoutResponse logoutResponse) {
        if (logoutResponse == null || logoutResponse.message == null) {
            return null;
        } else {
            return logoutResponse;
        }
    }
}
