package ru.nsu.fit.semenov.restchatwebsocket.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import ru.nsu.fit.semenov.restchatwebsocket.Message;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.Token;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserInfo;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.UserManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class WebsocketHandler implements WebSocketConnectionCallback {
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final UserManager userManager;
    private final Map<WebSocketChannel, UserInfo> channelsToUserInfosMap = new HashMap<>();

    public WebsocketHandler(UserManager userManager) {
        this.userManager = userManager;
    }

    public void broadcastMessage(Message message) {
        String responseAsString = gson.toJson(message);

        for (WebSocketChannel session : channelsToUserInfosMap.keySet()) {
            WebSockets.sendText(responseAsString, session, null);
        }
    }

    public void broadcastLogin(LoginInfo loginInfo) {
        String responseAsString = gson.toJson(loginInfo);

        for (WebSocketChannel session : channelsToUserInfosMap.keySet()) {
            WebSockets.sendText(responseAsString, session, null);
        }
    }

    public void broadcastLogout(LogoutInfo logoutInfo) {
        String responseAsString = gson.toJson(logoutInfo);

        for (WebSocketChannel session : channelsToUserInfosMap.keySet()) {
            WebSockets.sendText(responseAsString, session, null);
        }
    }

    public void sendKeepAlive() {
        ByteBuffer buffer = ByteBuffer.allocate(4).put("ping".getBytes(UTF_8));
        for (WebSocketChannel session : channelsToUserInfosMap.keySet()) {
            WebSockets.sendPing(buffer, session, null);
        }
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                // receive messages only from unauthorised websocket clients
                if (channelsToUserInfosMap.containsKey(channel)) {
                    if (message.getData().equals("close")) {
                        channelsToUserInfosMap.remove(channel);
                    }
                }

                final String authString = message.getData();

                Token token;
                try {
                    token = new Token(authString);
                } catch (IllegalArgumentException e) {
                    exchange.endExchange();
                    return;
                }

                UserInfo userInfo = userManager.getUserByToken(token);
                if (userInfo == null) {
                    exchange.endExchange();
                    return;
                }

                channelsToUserInfosMap.put(channel, userInfo);
            }

            @Override
            protected void onFullCloseMessage(WebSocketChannel channel, BufferedBinaryMessage message) throws IOException {
                channelsToUserInfosMap.remove(channel);
            }

            @Override
            protected void onFullPongMessage(WebSocketChannel channel, BufferedBinaryMessage message) {
                if (channelsToUserInfosMap.containsKey(channel)) {
                    userManager.updateSessionActivity(channelsToUserInfosMap.get(channel));
                }
            }
        });
        channel.resumeReceives();
    }
}
