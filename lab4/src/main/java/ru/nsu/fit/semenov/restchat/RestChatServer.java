package ru.nsu.fit.semenov.restchat;

import com.google.gson.*;
import com.sun.istack.internal.NotNull;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.nsu.fit.semenov.restchat.RequestBodies.LoginRequestBody;
import ru.nsu.fit.semenov.restchat.ResponseBodies.LoginResponseBody;
import ru.nsu.fit.semenov.restchat.user.SessionInfo;
import ru.nsu.fit.semenov.restchat.user.UserInfo;
import ru.nsu.fit.semenov.restchat.user.UserManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

import static java.nio.charset.StandardCharsets.UTF_8;

public class RestChatServer implements Runnable {
    private static final int BACKLOG = 10;
    private final int port;

    private UserManager userManager = new UserManager();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

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

            server.start();

        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
    }

    private void createLoginContext(@NotNull HttpServer server) {
        server.createContext("/login", http -> {
            if (http.getRequestMethod().equals("POST") &&
                    "application/json".equals(http.getRequestHeaders().getFirst("Content-Type"))
            ) {
                Reader requestBodyReader = new InputStreamReader(http.getRequestBody());
                LoginRequestBody requestBody;

                try {
                    requestBody = gson.fromJson(requestBodyReader, LoginRequestBody.class);
                    if (requestBody == null) {
                        sendError(http, HttpURLConnection.HTTP_BAD_REQUEST);
                        return;
                    }
                } catch (JsonIOException | JsonSyntaxException e) {
                    System.err.println(e.getMessage());
                    sendError(http, HttpURLConnection.HTTP_BAD_REQUEST);
                    return;
                }

                String username = requestBody.getUsername();
                if (username == null) {
                    sendError(http, HttpURLConnection.HTTP_BAD_REQUEST);
                    return;
                }

                UserInfo user = userManager.findUserByName(username);
                if (user == null) {
                    user = userManager.registerUser(username);
                }

                if (userManager.getUserSession(user) == null) {
                    SessionInfo sessionInfo = userManager.openSessionForUser(user);

                    LoginResponseBody responseBody = new LoginResponseBody(
                            user.getId(),
                            username,
                            true,
                            sessionInfo.getSessionToken().toString());

                    String responseBodyAsString = gson.toJson(responseBody);

                    System.out.println(responseBodyAsString);

                    byte[] respInBytes = responseBodyAsString.getBytes(UTF_8);
                    http.sendResponseHeaders(HttpURLConnection.HTTP_OK, respInBytes.length);

                    http.getResponseBody().write(respInBytes);
                    http.getResponseBody().close();

                } else {
                    http.getResponseHeaders().add(" WWW-Authenticate", "Token realm='Username is already in use'");
                    http.sendResponseHeaders(HttpURLConnection.HTTP_UNAUTHORIZED, -1);
                    http.getResponseBody().close();
                }
            } else {
                sendError(http, HttpURLConnection.HTTP_BAD_REQUEST);
            }
        });
    }

    private static void sendError(@NotNull HttpExchange http, int errorCode) throws IOException {
        http.sendResponseHeaders(errorCode, -1);
        http.getResponseBody().close();
    }
}
