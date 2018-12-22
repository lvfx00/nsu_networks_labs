package ru.nsu.fit.semenov.restchatwebsocket.websocket;

import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserInfo;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserOnlineState;

public class LogoutInfo {
    private final UserInfo userLogoutInfo;
    private final UserOnlineState logoutReason;

    public static @Nullable LogoutInfo checkForNulls(@Nullable LogoutInfo logoutInfo) {
        if (logoutInfo == null ||
                logoutInfo.userLogoutInfo == null ||
                logoutInfo.logoutReason == null) {
            return null;
        }
        return logoutInfo;
    }

    public LogoutInfo(UserInfo userLogoutInfo, UserOnlineState logoutReason) {
        this.userLogoutInfo = userLogoutInfo;
        this.logoutReason = logoutReason;
    }

    public UserInfo getUserLogoutInfo() {
        return userLogoutInfo;
    }

    public UserOnlineState getLogoutReason() {
        return logoutReason;
    }
}
