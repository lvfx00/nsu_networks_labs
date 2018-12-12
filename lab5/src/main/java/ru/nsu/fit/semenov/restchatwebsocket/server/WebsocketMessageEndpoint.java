package ru.nsu.fit.semenov.restchatwebsocket.server;

import org.jetbrains.annotations.NotNull;
import ru.nsu.fit.semenov.restchatwebsocket.requests.SendMessageRequest;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.Token;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserInfo;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserManager;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserState;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ServerEndpoint(value ="message/{token}")
public class WebsocketMessageEndpoint {
    private final UserManager userManager;

    private static final Map<String, UserInfo> sessionIdsToUserInfosMap = new HashMap<>();

    public WebsocketMessageEndpoint(@NotNull UserManager userManager) {
        this.userManager = userManager;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String tokenAsString) throws IOException {
        Token token;
        try {
            token = new Token(tokenAsString);
        } catch (IllegalArgumentException iae) {
            // TODO add close reason - invalid token format
            session.close();
            return;
        }

        UserInfo userInfo = userManager.getUserByToken(token);
        if (userInfo == null) {
            // TODO add close reason - there is no online user with specified token
            session.close();
            return;
        }

//        sessionsToUserInfosMap.put(session, userInfo);

        UserState userState = userManager.getUserStateByUserInfo(userInfo);
        if (userState == null) {
            throw new AssertionError("Unable to find user state by user info");
        }

        userState.setThroughWebsocket(true);
    }

//    @OnMessage
//    public void onPongMessage(Session session, PongMessage message) throws IOException {
////        UserInfo userInfo = sessionsToUserInfosMap.get(session);
////        if (userInfo == null) {
//            // there is no corresponding user to this session
//            session.close();
//            return;
//        }
//
////        userManager.updateSessionActivity(userInfo);
//    }

    @OnClose
    public void onClose(Session session) throws IOException {
    }
}
