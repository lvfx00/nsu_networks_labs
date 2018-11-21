package ru.nsu.fit.semenov.restchat.user;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserManager {
    private final ArrayList<UserInfo> indexedUserInfos = new ArrayList<>();
    private final Map<String, UserInfo> nicknamesToInfosMap = new HashMap<>();

    private final Map<UUID, SessionInfo> tokensToSessionsMap = new HashMap<>();
    private final Map<UserInfo, SessionInfo> usersToSessionsMap = new HashMap<>();
    private final Map<SessionInfo, UserInfo> sessionsToUsersMap = new HashMap<>();

    private int userCount;

    public UserManager() {
        userCount = 0;
    }

    public @NotNull UserInfo registerUser(@NotNull String username) {
        if (nicknamesToInfosMap.containsKey(username)) {
            throw new IllegalArgumentException("User with specified name already registered");
        }
        if (userCount == Integer.MAX_VALUE) {
            throw new IllegalStateException("Maximal amount of users already registered");
        }

        UserInfo newUser = new UserInfo(username, ++userCount);

        indexedUserInfos.add(newUser);
        nicknamesToInfosMap.put(username, newUser);

        return newUser;
    }

    public @Nullable UserInfo findUserByName(@NotNull String username) {
        return nicknamesToInfosMap.get(username);
    }

    public @NotNull SessionInfo openSessionForUser(@NotNull UserInfo user) {
        if (usersToSessionsMap.containsKey(user)) {
            throw new IllegalStateException("Specified user already has active session");
        }

        SessionInfo sessionInfo = new SessionInfo(UUID.randomUUID(), Instant.now());

        tokensToSessionsMap.put(sessionInfo.getSessionToken(), sessionInfo);
        sessionsToUsersMap.put(sessionInfo, user);
        usersToSessionsMap.put(user, sessionInfo);

        return sessionInfo;
    }

    public @Nullable SessionInfo getUserSession(@NotNull UserInfo user) {
        return usersToSessionsMap.get(user);
    }
}
