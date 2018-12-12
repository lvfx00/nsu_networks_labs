package ru.nsu.fit.semenov.restchatwebsocket.responses;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class UsersListResponse {
    private final List<UserInfoResponse> users;

    public static @Nullable UsersListResponse checkForNulls(@Nullable UsersListResponse usersListResponse) {
        if (usersListResponse == null || usersListResponse.users == null) {
            return null;
        }
        return usersListResponse;
    }

    public UsersListResponse(List<UserInfoResponse> users) {
        this.users = users;
    }

    public List<UserInfoResponse> getUsers() {
        return Collections.unmodifiableList(users);
    }
}
