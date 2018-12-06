package ru.nsu.fit.semenov.restchat;

import com.google.common.base.Splitter;
import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchat.message.MessageManager;
import ru.nsu.fit.semenov.restchat.user.*;
import ru.nsu.fit.semenov.restchatutil.Message;
import ru.nsu.fit.semenov.restchatutil.requests.LoginRequest;
import ru.nsu.fit.semenov.restchatutil.requests.SendMessageRequest;
import ru.nsu.fit.semenov.restchatutil.responses.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

public class RestChatServer implements Runnable {
    private static final int BACKLOG = 10;
    private final int port;

    private static final String TOKEN_BEGINNING = "Token ";
    private static final int BEGINNING_LENGHT = TOKEN_BEGINNING.length();

    private UserManager userManager = new UserManager();
    private MessageManager messageManager = new MessageManager();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Executor keepAliveChecker = Executors.newSingleThreadExecutor();

    public RestChatServer(int port) {
        this.port = port;

    }

    @Override
    public void run() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), BACKLOG);

            server.createContext("/hello", http -> {
                http.getResponseHeaders().add("Content-type", "text/plain");
                http.sendResponseHeaders(200, 0);

                String response = "Hello " + http.getRemoteAddress().getHostName();

                http.getResponseBody().write(response.getBytes(UTF_8));
                http.getResponseBody().close();
            });

            createLoginContext(server);
            createLogoutContext(server);
            createUsersListContext(server);
            createUserInfoContext(server);
            createMessagesContext(server);

            keepAliveChecker.execute(() -> {
                while (true) {
                    userManager.checkSessionActivities();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });

            server.start();

        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
    }

    private void createLoginContext(@NotNull HttpServer server) {
        checkNotNull(server);
        server.createContext("/login", http -> {
            if (!http.getRequestMethod().equals("POST")) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_METHOD);
                return;
            }

            if (!"application/json".equals(http.getRequestHeaders().getFirst("Content-Type"))) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            Reader requestBodyReader = new InputStreamReader(http.getRequestBody());
            LoginRequest loginRequest;

            try {
                loginRequest = gson.fromJson(requestBodyReader, LoginRequest.class);
            } catch (JsonIOException | JsonSyntaxException e) {
                System.err.println(e.getMessage());
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            if (loginRequest == null) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            String username = loginRequest.getUsername();
            if (username == null) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            UserInfo user = userManager.findUserByName(username);
            if (user == null) {
                user = userManager.registerUser(username);
            }

            if (userManager.getSessionInfoByUser(user) == null) {
                SessionInfo sessionInfo = userManager.openSessionForUser(user);

                String responseBodyAsString = gson.toJson(new LoginResponse(
                        user.getId(),
                        username,
                        true,
                        sessionInfo.getSessionToken().toString()));

                http.getResponseHeaders().add("Content-Type", "application/json");
                sendOkResponse(http, responseBodyAsString);

            } else {
                http.getResponseHeaders().add(" WWW-Authenticate", "Token realm='Username is already in use'");
                http.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1);
                http.getResponseBody().close();
            }
        });
    }

    private void createLogoutContext(@NotNull HttpServer server) {
        checkNotNull(server);
        server.createContext("/logout", http -> {
            if (!http.getRequestMethod().equals("POST")) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_METHOD);
                return;
            }

            Token authToken = extractAuthToken(http);
            if (authToken == null) {
                sendShortResponse(http, HttpURLConnection.HTTP_UNAUTHORIZED);
                return;
            }

            try {
                userManager.closeSessionByToken(authToken);

                String responseString = gson.toJson(new LogoutResponse("bye!"));

                http.getResponseHeaders().add("Content-Type", "application/json");
                sendOkResponse(http, responseString);

            } catch (IllegalStateException e) {
                sendShortResponse(http, HttpURLConnection.HTTP_FORBIDDEN);
            }
        });
    }

    private void createUsersListContext(@NotNull HttpServer server) {
        checkNotNull(server);
        server.createContext("/users", http -> {
            if (!http.getRequestMethod().equals("GET")) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_METHOD);
                return;
            }

            Token authToken = extractAuthToken(http);
            if (authToken == null) {
                sendShortResponse(http, HttpURLConnection.HTTP_UNAUTHORIZED);
                return;
            }

            if (!userManager.isTokenValid(authToken)) {
                sendShortResponse(http, HttpURLConnection.HTTP_FORBIDDEN);
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

            http.getResponseHeaders().add("Content-Type", "application/json");
            sendOkResponse(http, responseString);
        });
    }

    private void createUserInfoContext(@NotNull HttpServer server) {
        checkNotNull(server);
        server.createContext("/user/", http -> {
            if (!http.getRequestMethod().equals("GET")) {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_METHOD);
                return;
            }

            Token authToken = extractAuthToken(http);
            if (authToken == null) {
                sendShortResponse(http, HttpURLConnection.HTTP_UNAUTHORIZED);
                return;
            }

            if (!userManager.isTokenValid(authToken)) {
                sendShortResponse(http, HttpURLConnection.HTTP_FORBIDDEN);
                return;
            }

            userManager.updateSessionActivity(authToken);

            URI requestUri = http.getRequestURI();
            String path = requestUri.getPath();

            if (path.startsWith("/user/")) {
                try {
                    int id = Integer.valueOf(path.substring("/user/".length()));

                    UserInfo userInfo = userManager.findUserById(id);
                    if (userInfo == null) {
                        sendShortResponse(http, HttpURLConnection.HTTP_NOT_FOUND);
                        return;
                    }

                    UserState userState = userManager.getUserStateByUserInfo(userInfo);
                    if (userState == null) {
                        System.err.println("Unable to find state info for user " + userInfo.getUsername());
                        sendShortResponse(http, HttpURLConnection.HTTP_NOT_FOUND);
                        return;
                    }

                    UserInfoResponse userInfoResponse = new UserInfoResponse(id,
                            userInfo.getUsername(),
                            userState.getOnlineState().toString());

                    String responseString = gson.toJson(userInfoResponse);

                    http.getResponseHeaders().add("Content-Type", "application/json");
                    sendOkResponse(http, responseString);

                } catch (NumberFormatException e) {
                    System.err.println(e.getMessage());
                    sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                }
            } else {
                // never reaches that???
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
            }
        });
    }

    private void createMessagesContext(@NotNull HttpServer server) {
        checkNotNull(server);
        server.createContext("/messages", http -> {
            if (http.getRequestMethod().equals("GET")) {
                invokeMessagesGETcontext(http);

            } else if (http.getRequestMethod().equals("POST")) {
                invokeMessagesPOSTcontext(http);

            } else {
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_METHOD);
            }
        });
    }

    private void invokeMessagesPOSTcontext(@NotNull HttpExchange http) throws IOException {
        if (!"application/json".equals(http.getRequestHeaders().getFirst("Content-Type"))) {
            sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
            return;
        }

        Token authToken = extractAuthToken(http);
        if (authToken == null) {
            sendShortResponse(http, HttpURLConnection.HTTP_UNAUTHORIZED);
            return;
        }

        UserInfo userInfo = userManager.getUserByToken(authToken);
        if (userInfo == null) {
            sendShortResponse(http, HttpURLConnection.HTTP_FORBIDDEN);
            return;
        }

        userManager.updateSessionActivity(authToken);

        Reader requestBodyReader = new InputStreamReader(http.getRequestBody());
        SendMessageRequest sendMessageRequest;

        try {
            sendMessageRequest = gson.fromJson(requestBodyReader, SendMessageRequest.class);
        } catch (JsonIOException | JsonSyntaxException e) {
            System.err.println(e.getMessage());
            sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
            return;
        }

        if (sendMessageRequest == null || sendMessageRequest.getMessage() == null) {
            sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
            return;
        }

        Message msg = messageManager.sendMessage(sendMessageRequest.getMessage(), userInfo);

        SendMessageResponse sendMessageResponse = new SendMessageResponse(msg.getMessageText(), msg.getId());

        String responseAsString = gson.toJson(sendMessageResponse);

        http.getResponseHeaders().add("Content-Type", "application/json");
        sendOkResponse(http, responseAsString);
    }

    private void invokeMessagesGETcontext(@NotNull HttpExchange http) throws IOException {
        Token authToken = extractAuthToken(http);
        if (authToken == null) {
            sendShortResponse(http, HttpURLConnection.HTTP_UNAUTHORIZED);
            return;
        }

        if (!userManager.isTokenValid(authToken)) {
            sendShortResponse(http, HttpURLConnection.HTTP_FORBIDDEN);
            return;
        }

        userManager.updateSessionActivity(authToken);

        int offset = 0;
        int count = 10;

        URI requestUri = http.getRequestURI();
        String query = requestUri.getQuery();

        if (query != null) {
            Map<String, String> queryMap;

            try {
                queryMap = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(query);
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
                sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                return;
            }

            if (queryMap.containsKey("offset")) {
                try {
                    offset = Integer.valueOf(queryMap.get("offset"));
                } catch (NumberFormatException e) {
                    System.err.println(e.getMessage());
                    sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                    return;
                }
            }

            if (queryMap.containsKey("count")) {
                try {
                    count = Integer.valueOf(queryMap.get("count"));
                } catch (NumberFormatException e) {
                    System.err.println(e.getMessage());
                    sendShortResponse(http, HttpURLConnection.HTTP_BAD_REQUEST);
                    return;
                }
            }
        }

        List<Message> messages = messageManager.getMessages(offset, count);

        MessagesListResponse messagesListResponse = new MessagesListResponse(messages);

        String responseString = gson.toJson(messagesListResponse);

        http.getResponseHeaders().add("Content-Type", "application/json");
        sendOkResponse(http, responseString);
    }

    private static @Nullable Token extractAuthToken(@NotNull HttpExchange http) {
        String authString = checkNotNull(http).getRequestHeaders().getFirst("Authorization");
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

    private static void sendShortResponse(@NotNull HttpExchange http, int errorCode) throws IOException {
        checkNotNull(http).sendResponseHeaders(errorCode, -1);
        http.getResponseBody().close();
    }

    // you have to fill headers of httpExchange before invoking this method
    private static void sendOkResponse(@NotNull HttpExchange http, @NotNull String responseBody) throws IOException {
        byte[] responseBodyInBytes = checkNotNull(responseBody).getBytes(UTF_8);

        checkNotNull(http).sendResponseHeaders(HttpURLConnection.HTTP_OK, responseBodyInBytes.length);

        http.getResponseBody().write(responseBodyInBytes);
        http.getResponseBody().close();
    }
}
