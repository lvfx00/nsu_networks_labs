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

    public @Nullable UserInfo update(@NotNull UserInfoResponse newUserData) {
        if (userInfosMap.containsKey(newUserData.getId())) {
            if (!newUserData.getOnline().equals(userInfosMap.get(newUserData.getId()).getOnline())) {
                userInfosMap.get(newUserData.getId()).setOnline(newUserData.getOnline());

                return userInfosMap.get(newUserData.getId());
            }
        } else {
            userInfosMap.put(newUserData.getId(), new UserInfo(newUserData));

            if (newUserData.getOnline().equals("true")) {
                return userInfosMap.get(newUserData.getId());
            }
        }
        return null;
    }

    public @Nullable String getNameById(int id) {
        checkArgument(id > 0);
        if (userInfosMap.containsKey(id)) {
            return userInfosMap.get(id).getUsername();
        }
        return null;
    }
}
