package ru.nsu.fit.semenov.restchat.user;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class UserManager {
    private static final Duration MAX_INACTIVITY_TIME = Duration.ofMillis(5000);

    private final ArrayList<UserInfo> indexedUserInfos = new ArrayList<>();

    private final HashMap<UserInfo, UserState> userStatesMap = new HashMap<>();
    private final Map<String, UserInfo> nicknamesToInfosMap = new HashMap<>();

    private final Map<Token, SessionInfo> tokensToSessionsMap = new HashMap<>();

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
        userStatesMap.put(newUser, new UserState(UserOnlineState.OFFLINE));

        return newUser;
    }

    public @Nullable UserInfo findUserByName(@NotNull String username) {
        return nicknamesToInfosMap.get(username);
    }

    public @Nullable UserInfo findUserById(int id) {
        try {
            return indexedUserInfos.get(id - 1);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public @NotNull SessionInfo openSessionForUser(@NotNull UserInfo user) {
        if (usersToSessionsMap.containsKey(user)) {
            throw new IllegalStateException("Specified user already has active session");
        }

        SessionInfo sessionInfo = new SessionInfo(new Token(UUID.randomUUID()), Instant.now());

        tokensToSessionsMap.put(sessionInfo.getSessionToken(), sessionInfo);
        sessionsToUsersMap.put(sessionInfo, user);
        usersToSessionsMap.put(user, sessionInfo);

        userStatesMap.get(user).setOnlineState(UserOnlineState.ONLINE);

        return sessionInfo;
    }

    public void closeSessionByToken(@NotNull Token token) throws IllegalStateException {
        SessionInfo sessionInfo = tokensToSessionsMap.get(checkNotNull(token));
        if (sessionInfo == null) {
            throw new IllegalStateException("Unable to find session with specified token");
        }
        UserInfo userInfo = sessionsToUsersMap.get(sessionInfo);
        if (userInfo == null) {
            throw new IllegalStateException("Unable to find user for session associated with specified token");
        }
        sessionsToUsersMap.remove(sessionInfo);
        usersToSessionsMap.remove(userInfo);
        userStatesMap.get(userInfo).setOnlineState(UserOnlineState.OFFLINE);
    }

    public @Nullable SessionInfo getSessionInfoByUser(@NotNull UserInfo user) {
        return usersToSessionsMap.get(checkNotNull(user));
    }

    public @Nullable SessionInfo getSessionInfoByToken(@NotNull Token token) {
        return tokensToSessionsMap.get(checkNotNull(token));
    }

    public boolean isTokenValid(@NotNull Token token) {
        return tokensToSessionsMap.containsKey(checkNotNull(token));
    }

    public @Nullable UserState getUserStateByUserInfo(@NotNull UserInfo userInfo) {
        return userStatesMap.get(checkNotNull(userInfo));
    }

    public @NotNull Map<UserInfo, UserState> getUserStatesMap() {
        return Collections.unmodifiableMap(userStatesMap);
    }

    public @Nullable UserInfo getUserByToken(@NotNull Token token) {
        SessionInfo sessionInfo = tokensToSessionsMap.get(checkNotNull(token));
        if (sessionInfo == null) {
            return  null;
        }
        return sessionsToUsersMap.get(sessionInfo);
    }

    public void updateSessionActivity(@NotNull Token token) {
        SessionInfo sessionInfo = tokensToSessionsMap.get(token);
        if (sessionInfo != null) {
            sessionInfo.setLastActivity(Instant.now());
        }
    }

    public void checkSessionActivities() {
        Instant now = Instant.now();

        Set<Token> tokensToRemove = new HashSet<>();
        Set<UserState> usersToSetTimedOut = new HashSet<>();

        for (SessionInfo sessionInfo : sessionsToUsersMap.keySet()) {
            UserInfo userInfo = sessionsToUsersMap.get(sessionInfo);

            if (Duration.between(sessionInfo.getLastActivity(), now).compareTo(MAX_INACTIVITY_TIME) > 0) {
                tokensToRemove.add(sessionInfo.getSessionToken());

                UserState userState = userStatesMap.get(userInfo);
                usersToSetTimedOut.add(userState);
            }
        }

        for (Token tk : tokensToRemove) {
            closeSessionByToken(tk);
        }
        for (UserState userState : usersToSetTimedOut) {
            userState.setOnlineState(UserOnlineState.TIMED_OUT);
        }
    }
}
