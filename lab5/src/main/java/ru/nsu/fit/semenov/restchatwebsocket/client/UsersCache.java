package ru.nsu.fit.semenov.restchatwebsocket.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatwebsocket.responses.UserInfoResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

public class UsersCache {
    private final Map<Integer, UserInfo> userInfosMap = new HashMap<>();

    public @NotNull List<UserInfo> update(@NotNull List<UserInfoResponse> newUserData) {
        List<UserInfo> updates = new ArrayList<>();

        for (UserInfoResponse userInfo : newUserData) {
            if (userInfosMap.containsKey(userInfo.getId())) {
                if (!userInfo.getOnline().equals(userInfosMap.get(userInfo.getId()).getOnline())) {
                    userInfosMap.get(userInfo.getId()).setOnline(userInfo.getOnline());

                    updates.add(userInfosMap.get(userInfo.getId()));
                }

            } else {
                userInfosMap.put(userInfo.getId(), new UserInfo(userInfo));

                if (userInfo.getOnline().equals("true")) {
                    updates.add(userInfosMap.get(userInfo.getId()));
                }
            }
        }

        return updates;
    }

    public @Nullable String getNameById(int id) {
        checkArgument(id > 0);
        if (userInfosMap.containsKey(id)) {
            return userInfosMap.get(id).getUsername();
        }
        return null;
    }
}
