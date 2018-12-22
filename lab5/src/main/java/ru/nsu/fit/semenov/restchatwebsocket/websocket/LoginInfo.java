package ru.nsu.fit.semenov.restchatwebsocket.websocket;

import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserInfo;

public class LoginInfo {
    private final UserInfo userLoginInfo;

    public static @Nullable LoginInfo checkForNulls(@Nullable LoginInfo loginInfo) {
        if (loginInfo == null || loginInfo.userLoginInfo == null) {
            return null;
        }
        return loginInfo;
    }

    public LoginInfo(UserInfo userLoginInfo) {
        this.userLoginInfo = userLoginInfo;
    }

    public UserInfo getUserLoginInfo() {
        return userLoginInfo;
    }
}
