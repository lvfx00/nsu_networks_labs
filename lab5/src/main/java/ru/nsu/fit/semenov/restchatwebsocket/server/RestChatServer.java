package ru.nsu.fit.semenov.restchatwebsocket.server;

import com.google.gson.*;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatwebsocket.Message;
import ru.nsu.fit.semenov.restchatwebsocket.requests.LoginRequest;
import ru.nsu.fit.semenov.restchatwebsocket.requests.SendMessageRequest;
import ru.nsu.fit.semenov.restchatwebsocket.responses.*;
import ru.nsu.fit.semenov.restchatwebsocket.server.message.MessageManager;
import ru.nsu.fit.semenov.restchatwebsocket.server.user.*;

import io.undertow.Undertow;
import ru.nsu.fit.semenov.restchatwebsocket.websocket.LoginInfo;
import ru.nsu.fit.semenov.restchatwebsocket.websocket.LogoutInfo;
import ru.nsu.fit.semenov.restchatwebsocket.websocket.WebsocketHandler;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.undertow.Handlers.path;
import static io.undertow.Handlers.websocket;
import static java.net.HttpURLConnection.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RestChatServer implements Runnable {
    private final int port;

    public static final String TOKEN_BEGINNING = "Token ";
    public static final int BEGINNING_LENGHT = TOKEN_BEGINNING.length();

    private UserManager userManager = new UserManager();
    private MessageManager messageManager = new MessageManager();

    private WebsocketHandler websocketHandler = new WebsocketHandler(userManager);

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Executor keepAliveChecker = Executors.newSingleThreadExecutor();

    public RestChatServer(int port) {
        userManager.setWebsocketHandler(websocketHandler);
        this.port = port;
    }

    public void run() {
        Undertow server = Undertow.builder()
                .addHttpListener(port, "localhost")
                .setHandler(path()
                        .addExactPath("/hello", http -> {
                            http.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/plain");

                            http.getResponseSender().send("Hello " + http.getHostName());
                        })
                        .addExactPath("/login", this::loginHandler)
                        .addExactPath("/logout", this::logoutHandler)
                        .addExactPath("/users", this::usersListHandler)
                        .addPrefixPath("/user/", this::userInfoHandler)
                        .addExactPath("/messages", this::messagesHandler)
                        .addExactPath("/messages-websocket", websocket(websocketHandler))
                ).build();

        keepAliveChecker.execute(() -> {
            while (true) {
                userManager.checkSessionActivities();
                websocketHandler.sendKeepAlive();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        server.start();
    }

    private void loginHandler(HttpServerExchange httpExchange) {
        if (!httpExchange.getRequestMethod().toString().equals("POST")) {
            httpExchange.setStatusCode(HTTP_BAD_METHOD);
            httpExchange.endExchange();
            return;
        }

        if (!httpExchange.getRequestHeaders().contains(Headers.CONTENT_TYPE) ||
                !httpExchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).equals("application/json")) {
            httpExchange.setStatusCode(HTTP_BAD_REQUEST);
            httpExchange.endExchange();
            return;
        }

        httpExchange.getRequestReceiver().receiveFullString(this::loginMessageHandler, UTF_8);
    }

    // used in loginHandler
    private void loginMessageHandler(HttpServerExchange exchange, String message) {
        LoginRequest loginRequest;

        try {
            loginRequest = gson.fromJson(message, LoginRequest.class);

        } catch (JsonIOException | JsonSyntaxException e) {
            exchange.setStatusCode(HTTP_BAD_REQUEST);
            exchange.endExchange();
            return;
        }

        loginRequest = LoginRequest.checkForNulls(loginRequest);

        if (loginRequest == null) {
            exchange.setStatusCode(HTTP_BAD_REQUEST);
            exchange.endExchange();
            return;
        }

        String username = loginRequest.getUsername();

        UserInfo user = userManager.findUserByName(username);
        if (user == null) {
            user = userManager.registerUser(username);
        }

        if (userManager.getSessionInfoByUser(user) == null) {
            SessionInfo sessionInfo = userManager.openSessionForUser(user);

            websocketHandler.broadcastLogin(new LoginInfo(user));

            String responseBodyAsString = gson.toJson(new LoginResponse(
                    user.getId(),
                    username,
                    true,
                    sessionInfo.getSessionToken().toString()));

            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");

            exchange.getResponseSender().send(responseBodyAsString, UTF_8);

        } else {
            exchange.getResponseHeaders()
                    .add(Headers.WWW_AUTHENTICATE, "Token realm='Username is already in use'");
            exchange.setStatusCode(HTTP_UNAUTHORIZED);
        }

        exchange.endExchange();
    }

    private void logoutHandler(HttpServerExchange httpExchange) {
        if (!httpExchange.getRequestMethod().toString().equals("POST")) {
            httpExchange.setStatusCode(HTTP_BAD_METHOD);
            httpExchange.endExchange();
            return;
        }

        Token authToken = extractAuthToken(httpExchange);
        if (authToken == null) {
            httpExchange.setStatusCode(HTTP_UNAUTHORIZED);
            httpExchange.endExchange();
            return;
        }

        UserInfo userInfo = userManager.getUserByToken(authToken);
        if (userInfo == null) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
        }

        try {
            userManager.closeSessionByToken(authToken, UserOnlineState.OFFLINE);

            websocketHandler.broadcastLogout(new LogoutInfo(userInfo, UserOnlineState.OFFLINE));

            httpExchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
            String responseString = gson.toJson(new LogoutResponse("bye!"));
            httpExchange.getResponseSender().send(responseString, UTF_8);

        } catch (IllegalStateException e) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
        }
    }

    private void usersListHandler(HttpServerExchange httpExchange) {
        if (!httpExchange.getRequestMethod().toString().equals("GET")) {
            httpExchange.setStatusCode(HTTP_BAD_METHOD);
            httpExchange.endExchange();
            return;
        }

        Token authToken = extractAuthToken(httpExchange);
        if (authToken == null) {
            httpExchange.setStatusCode(HTTP_UNAUTHORIZED);
            httpExchange.endExchange();
            return;
        }

        if (!userManager.isTokenValid(authToken)) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
            return;
        }

        userManager.updateSessionActivity(authToken);

        Map<UserInfo, UserState> states = userManager.getUserStatesMap();

        List<UserInfoResponse> userInfoResponses = new ArrayList<>();

        for (Map.Entry<UserInfo, UserState> userData : states.entrySet()) {
            userInfoResponses.add(
                    new UserInfoResponse(userData.getKey().getId(),
                            userData.getKey().getUsername(),
                            userData.getValue().getOnlineState().toString()));
        }

        UsersListResponse usersListResponse = new UsersListResponse(userInfoResponses);

        String responseString = gson.toJson(usersListResponse);

        httpExchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        httpExchange.getResponseSender().send(responseString, UTF_8);
    }

    private void userInfoHandler(HttpServerExchange httpExchange) {
        if (!httpExchange.getRequestMethod().toString().equals("GET")) {
            httpExchange.setStatusCode(HTTP_BAD_METHOD);
            httpExchange.endExchange();
            return;
        }

        Token authToken = extractAuthToken(httpExchange);
        if (authToken == null) {
            httpExchange.setStatusCode(HTTP_UNAUTHORIZED);
            httpExchange.endExchange();
            return;
        }

        if (!userManager.isTokenValid(authToken)) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
            return;
        }

        userManager.updateSessionActivity(authToken);

        String path = httpExchange.getRequestURI();
        if (path.startsWith("/user/")) {
            try {
                int id = Integer.valueOf(path.substring("/user/".length()));

                UserInfo userInfo = userManager.findUserById(id);
                if (userInfo == null) {
                    httpExchange.setStatusCode(HTTP_NOT_FOUND);
                    httpExchange.endExchange();
                    return;
                }

                UserState userState = userManager.getUserStateByUserInfo(userInfo);
                if (userState == null) {
                    System.err.println("ERROR: Unable to find state info for user " + userInfo.getUsername());

                    httpExchange.setStatusCode(HTTP_NOT_FOUND);
                    httpExchange.endExchange();
                    return;
                }

                UserInfoResponse userInfoResponse =
                        new UserInfoResponse(id,
                                userInfo.getUsername(),
                                userState.getOnlineState().toString());

                String responseString = gson.toJson(userInfoResponse);

                httpExchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
                httpExchange.getResponseSender().send(responseString, UTF_8);

            } catch (NumberFormatException e) {
                System.err.println(e.getMessage());

                httpExchange.setStatusCode(HTTP_BAD_REQUEST);
                httpExchange.endExchange();
            }
        } else {
            // never reaches that???
            httpExchange.setStatusCode(HTTP_BAD_REQUEST);
            httpExchange.endExchange();
        }
    }

    private void messagesHandler(HttpServerExchange httpExchange) {
        if (httpExchange.getRequestMethod().toString().equals("GET")) {
            messagesGETHandler(httpExchange);

        } else if (httpExchange.getRequestMethod().toString().equals("POST")) {
            messagesPOSTHandler(httpExchange);

        } else {
            httpExchange.setStatusCode(HTTP_BAD_METHOD);
            httpExchange.endExchange();
        }
    }

    private void messagesPOSTHandler(HttpServerExchange httpExchange) {
        if (!httpExchange.getRequestHeaders().contains(Headers.CONTENT_TYPE) ||
                !httpExchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE).equals("application/json")) {
            httpExchange.setStatusCode(HTTP_BAD_REQUEST);
            httpExchange.endExchange();
            return;
        }

        Token authToken = extractAuthToken(httpExchange);
        if (authToken == null) {
            httpExchange.setStatusCode(HTTP_UNAUTHORIZED);
            httpExchange.endExchange();
            return;
        }

        UserInfo userInfo = userManager.getUserByToken(authToken);
        if (userInfo == null) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
            return;
        }

        httpExchange.getRequestReceiver().receiveFullString((exchange, message) -> {
            SendMessageRequest sendMessageRequest;

            try {
                sendMessageRequest = gson.fromJson(message, SendMessageRequest.class);
            } catch (JsonIOException | JsonSyntaxException e) {
                System.err.println(e.getMessage());
                exchange.setStatusCode(HTTP_BAD_REQUEST);
                exchange.endExchange();
                return;
            }

            sendMessageRequest = SendMessageRequest.checkForNulls(sendMessageRequest);

            if (sendMessageRequest == null) {
                exchange.setStatusCode(HTTP_BAD_REQUEST);
                exchange.endExchange();
                return;
            }

            Message msg = messageManager.sendMessage(sendMessageRequest.getMessage(), userInfo);
            websocketHandler.broadcastMessage(msg);

            SendMessageResponse sendMessageResponse = new SendMessageResponse(msg.getMessageText(), msg.getId());

            String responseAsString = gson.toJson(sendMessageResponse);

            exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(responseAsString, UTF_8);

        }, UTF_8);
    }

    private void messagesGETHandler(HttpServerExchange httpExchange) {
        Token authToken = extractAuthToken(httpExchange);
        if (authToken == null) {
            httpExchange.setStatusCode(HTTP_UNAUTHORIZED);
            httpExchange.endExchange();
            return;
        }

        if (!userManager.isTokenValid(authToken)) {
            httpExchange.setStatusCode(HTTP_FORBIDDEN);
            httpExchange.endExchange();
            return;
        }

        userManager.updateSessionActivity(authToken);

        int offset = 0;
        int count = 10;

        Map<String, Deque<String>> queryMap = httpExchange.getQueryParameters();

        if (queryMap.containsKey("offset")) {
            try {
                offset = Integer.valueOf(queryMap.get("offset").getFirst());

            } catch (NumberFormatException e) {
                System.err.println(e.getMessage());
                httpExchange.setStatusCode(HTTP_BAD_REQUEST);
                httpExchange.endExchange();
                return;
            }
        }

        if (queryMap.containsKey("count")) {
            try {
                count = Integer.valueOf(queryMap.get("count").getFirst());

            } catch (NumberFormatException e) {
                System.err.println(e.getMessage());
                httpExchange.setStatusCode(HTTP_BAD_REQUEST);
                httpExchange.endExchange();
                return;
            }
        }

        List<Message> messages = messageManager.getMessages(offset, count);

        MessagesListResponse messagesListResponse = new MessagesListResponse(messages);

        String responseString = gson.toJson(messagesListResponse);

        httpExchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "application/json");
        httpExchange.getResponseSender().send(responseString, UTF_8);
    }

    private static @Nullable Token extractAuthToken(HttpServerExchange http) {
        String authString = checkNotNull(http).getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        if (authString == null) {
            return null;
        }

        if (authString.startsWith(TOKEN_BEGINNING)) {
            try {
                return new Token(authString.substring(BEGINNING_LENGHT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
