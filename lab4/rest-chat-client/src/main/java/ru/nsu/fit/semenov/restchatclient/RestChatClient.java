package ru.nsu.fit.semenov.restchatclient;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatutil.Message;
import ru.nsu.fit.semenov.restchatutil.requests.LoginRequest;
import ru.nsu.fit.semenov.restchatutil.requests.SendMessageRequest;
import ru.nsu.fit.semenov.restchatutil.responses.*;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RestChatClient implements Runnable {
    public static final String LOGOUT = "logout";
    public static final String USER_LIST = "list";
    public static final String USER_INFO = "info";

    public static final int MESSAGE_QUERY_NUM = 10;

    public final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Scanner inputScanner;
    private final PrintStream outputStream;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String hostname;
    private final int port;

    private final URI loginUri;
    private final URI logoutUri;
    private final URI messagesUriPOST;
    private final URI usersUri;

    public RestChatClient(@NotNull String hostname,
                          int port,
                          @NotNull Scanner in,
                          @NotNull PrintStream out) {

        this.hostname = checkNotNull(hostname);
        checkArgument(port > 0 && port < 65536);
        this.port = port;

        inputScanner = checkNotNull(in);
        outputStream = checkNotNull(out);

        try {
            loginUri = new URI("http", null, hostname, port, "/login", null, null);
            logoutUri = new URI("http", null, hostname, port, "/logout", null, null);
            messagesUriPOST = new URI("http", null, hostname, port, "/messages", null, null);
            usersUri = new URI("http", null, hostname, port, "/users", null, null);

        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            throw new IllegalArgumentException("Invalid hostname or port specified");
        }
    }

    @Override
    public void run() {
        try {
            boolean loggedIn = false;
            LoginResponse loginResponse = null;

            while (!loggedIn) {
                outputStream.println("Enter the username:");
                String username = inputScanner.nextLine();
                if (username == null || username.equals("exit")) {
                    outputStream.println("Exiting.");
                    return;
                }

                try {
                    loginResponse = login(username);
                    if (loginResponse == null) {
                        outputStream.println("Invalid response received. Exiting.");
                        return;
                    }

                    loggedIn = true;
                    outputStream.println("Successfully logged in as " + loginResponse.getUsername() +
                            " (id = " + loginResponse.getId() + " token = " + loginResponse.getToken() + ")");

                } catch (HttpResponseException e1) {
                    if (e1.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                        outputStream.println("Username already taken, try another one.");
                    } else {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        return;
                    }
                }
            }

            final String token = loginResponse.getToken();
            executor.execute(() -> {
                try {
                    checkUpdates(outputStream, token);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            });

            while (inputScanner.hasNextLine()) {
                String userInput = inputScanner.nextLine();
                if (userInput.equals(LOGOUT)) {
                    try {
                        LogoutResponse logoutResponse = logout(loginResponse.getToken());
                        if (logoutResponse == null) {
                            outputStream.println("Invalid response received. Exiting.");
                            break;
                        }

                        outputStream.println(logoutResponse.getMessage());
                        break;

                    } catch (HttpResponseException e1) {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        break;
                    }

                } else if (userInput.equals(USER_LIST)) {
                    UsersListResponse usersListResponse;

                    try {
                        usersListResponse = getUsers(token);
                    } catch (HttpResponseException e1) {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        break;
                    }

                    if (usersListResponse == null) {
                        outputStream.println("Invalid response received. Exiting.");
                        break;
                    }

                    for (UserInfoResponse user : usersListResponse.getUsers()) {
                        if (user.getOnline().equals("true")) {
                            outputStream.println(user.getUsername());
                        }
                    }

                } else if (userInput.equals(USER_INFO)) {
                    outputStream.println("Enter user id:");
                    String userIdAsString = inputScanner.nextLine();
                    int userId;
                    try {
                        userId = Integer.valueOf(userIdAsString);
                    } catch (NumberFormatException e) {
                        System.err.println(e.getMessage());
                        outputStream.println("Invalid id specified :(");
                        continue;
                    }

                    UserInfoResponse userInfoResponse;

                    try {
                        userInfoResponse = getUserInfo(token, userId);
                        if (userInfoResponse == null) {
                            outputStream.println("Invalid response received. Exiting.");
                            break;
                        }

                    } catch (HttpResponseException e1) {
                        if (e1.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                            outputStream.println("User with specified id not found");
                            continue;
                        }

                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        break;
                    }

                    outputStream.println("id = " + userInfoResponse.getId());
                    outputStream.println("username = " + userInfoResponse.getUsername());
                    outputStream.println("online = " + userInfoResponse.getOnline());

                } else { // just send message
                    try {
                        SendMessageResponse sendMessageResponse = sendMessage(userInput, loginResponse.getToken());
                        if (sendMessageResponse == null) {
                            outputStream.println("Invalid response received. Exiting.");
                            break;
                        }

                        // TODO remove later
                        outputStream.println(sendMessageResponse.getId() + ": " + sendMessageResponse.getMessage());

                    } catch (HttpResponseException e1) {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        break;
                    }
                }
            }
            executor.shutdownNow();
            outputStream.println("Exiting.");

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private @Nullable LoginResponse login(@NotNull String username) throws IOException, HttpResponseException {
        HttpPost httpPost = new HttpPost(loginUri);

        httpPost.addHeader("Content-Type", "application/json");

        LoginRequest loginRequest = new LoginRequest(username);
        String requestBodyAsString = gson.toJson(loginRequest);

        StringEntity stringEntity = new StringEntity(requestBodyAsString, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        LoginResponse loginResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to login");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            loginResponse = gson.fromJson(responseBodyAsString, LoginResponse.class);
        }

        return LoginResponse.checkForNulls(loginResponse);
    }

    private @Nullable LogoutResponse logout(@NotNull String token) throws IOException, HttpResponseException {
        HttpPost httpPost = new HttpPost(logoutUri);

        httpPost.addHeader("Authorization", "Token " + checkNotNull(token));

        LogoutResponse logoutResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to logout");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            logoutResponse = gson.fromJson(responseBodyAsString, LogoutResponse.class);
        }

        return LogoutResponse.checkForNulls(logoutResponse);
    }

    private @Nullable SendMessageResponse sendMessage(@NotNull String message, @NotNull String token)
            throws IOException, HttpResponseException {

        HttpPost httpPost = new HttpPost(messagesUriPOST);

        httpPost.addHeader("Authorization", "Token " + checkNotNull(token));
        httpPost.addHeader("Content-Type", "application/json");

        SendMessageRequest sendMessageRequest = new SendMessageRequest(checkNotNull(message));
        String requestBodyAsString = gson.toJson(sendMessageRequest);

        StringEntity stringEntity = new StringEntity(requestBodyAsString, ContentType.APPLICATION_JSON);
        httpPost.setEntity(stringEntity);

        SendMessageResponse sendMessageResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to send message");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            sendMessageResponse = gson.fromJson(responseBodyAsString, SendMessageResponse.class);
        }

        return SendMessageResponse.checkForNulls(sendMessageResponse);
    }

    private @Nullable UserInfoResponse getUserInfo(@NotNull String token, int userId) throws IOException, HttpResponseException {
        checkArgument(userId > 0);

        URI userUri;
        try {
            userUri = new URI("http", null, hostname, port, "/user/" + userId, null, null);
        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            throw new IllegalArgumentException("Invalid user id specified???");
        }

        HttpGet httpGet = new HttpGet(userUri);
        httpGet.addHeader("Authorization", "Token " + checkNotNull(token));

        UserInfoResponse userInfoResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to logout");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            userInfoResponse = gson.fromJson(responseBodyAsString, UserInfoResponse.class);
        }

        return UserInfoResponse.checkForNulls(userInfoResponse);
    }

    private @Nullable UsersListResponse getUsers(@NotNull String token) throws IOException, HttpResponseException {
        HttpGet httpGet = new HttpGet(usersUri);

        httpGet.addHeader("Authorization", "Token " + checkNotNull(token));

        UsersListResponse usersListResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to get users list");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            usersListResponse = gson.fromJson(responseBodyAsString, UsersListResponse.class);
        }

        return UsersListResponse.checkForNulls(usersListResponse);
    }

    private @Nullable MessagesListResponse getMessages(@NotNull String token, int count, int offset)
            throws IOException, HttpResponseException {
        checkArgument(count > -1);
        checkArgument(offset > -1);

        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("count", String.valueOf(count));
        queryParams.put("offset", String.valueOf(offset));

        String queryString = Joiner.on('&').withKeyValueSeparator('=').join(queryParams);

        URI messagesUriGET;
        try {
            messagesUriGET = new URI(
                    "http", null, hostname, port, "/messages", queryString, null);

        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            throw new IllegalArgumentException("Invalid count or offset specified");
        }

        HttpGet httpGet = new HttpGet(messagesUriGET);
        httpGet.addHeader("Authorization", "Token " + checkNotNull(token));

        MessagesListResponse messagesListResponse;

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK) {
                throw new HttpResponseException(statusCode, "Unable to logout");
            }

            HttpEntity responseEntity = response.getEntity();
            String responseBodyAsString = EntityUtils.toString(responseEntity);

            messagesListResponse = gson.fromJson(responseBodyAsString, MessagesListResponse.class);
        }

        return MessagesListResponse.checkForNulls(messagesListResponse);
    }

    private void checkUpdates(@NotNull PrintStream outputStream, @NotNull String token) throws IOException {
        checkNotNull(outputStream);
        checkNotNull(token);

        UsersCache usersCache = new UsersCache();
        int messagesCount = 0;

        while (true) {
            List<UserInfo> userUpdates;

            try {
                UsersListResponse usersListResponse = getUsers(token);
                if (usersListResponse == null) {
                    outputStream.println("Invalid response received. Exiting.");
                    return;
                }

                userUpdates = usersCache.update(usersListResponse.getUsers());

            } catch (HttpResponseException e1) {
                outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                return;
            }

            for (UserInfo userInfo : userUpdates) {
                switch (userInfo.getOnline()) {
                    case "true":
                        outputStream.println(userInfo.getUsername() + " logged in.");
                        break;
                    case "false":
                        break;
                    case "null":
                        break;
                    default:
                        throw new AssertionError("Invalid onlineStatus specified");
                }
            }


            int msgNum;
            do {
                try {
                    MessagesListResponse messagesListResponse = getMessages(token, MESSAGE_QUERY_NUM, messagesCount);
                    if (messagesListResponse == null) {
                        outputStream.println("Invalid response received. Exiting.");
                        return;
                    }

                    msgNum = messagesListResponse.getMessages().size();
                    messagesCount += msgNum;

                    for (Message msg : messagesListResponse.getMessages()) {
                        String authorUsername = usersCache.getNameById(msg.getAuthor());
                        if (authorUsername == null) {
                            authorUsername = "Unknown user";
                        }

                        outputStream.println(authorUsername + ": " + msg.getMessageText());
                    }

                } catch (HttpResponseException e1) {
                    outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                    return;
                }
            } while (msgNum == MESSAGE_QUERY_NUM);

            for (UserInfo userInfo : userUpdates) {
                switch (userInfo.getOnline()) {
                    case "true":
                        break;
                    case "false":
                        outputStream.println(userInfo.getUsername() + " logged out.");
                        break;
                    case "null":
                        outputStream.println(userInfo.getUsername() + " lost connection with the server.");
                        break;
                    default:
                        throw new AssertionError("Invalid onlineStatus specified");
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
