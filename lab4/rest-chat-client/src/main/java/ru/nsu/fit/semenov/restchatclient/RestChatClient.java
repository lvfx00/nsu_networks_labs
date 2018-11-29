package ru.nsu.fit.semenov.restchatclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.nsu.fit.semenov.restchatclient.requests.LoginRequest;
import ru.nsu.fit.semenov.restchatclient.requests.SendMessageRequest;
import ru.nsu.fit.semenov.restchatclient.responses.LoginResponse;
import ru.nsu.fit.semenov.restchatclient.responses.LogoutResponse;
import ru.nsu.fit.semenov.restchatclient.responses.SendMessageResponse;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RestChatClient implements Runnable {
    public static final String LOGOUT = "logout";
    public static final String USER_LIST = "list";

    public final Executor executor = Executors.newSingleThreadExecutor();

    private final Scanner inputScanner;
    private final PrintStream outputStream;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String hostname;
    private final int port;

    private final URI loginUri;
    private final URI logoutUri;
    private final URI messagesUri;

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
            messagesUri = new URI("http", null, hostname, port, "/messages", null, null);

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

            // TODO add stream that checks new messages here

            while (inputScanner.hasNextLine()) {
                String userInput = inputScanner.nextLine();
                if (userInput.equals(LOGOUT)) {
                    try {
                        LogoutResponse logoutResponse = logout(loginResponse.getToken());
                        if (logoutResponse == null) {
                            outputStream.println("Invalid response received. Exiting.");
                            return;
                        }

                        outputStream.println(logoutResponse.getMessage());
                        return;

                    } catch (HttpResponseException e1) {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        return;
                    }
                }
//                else if (userInput.equals(USER_LIST)) {
//
//                }
                else { // just send message
                    try {
                        SendMessageResponse sendMessageResponse = sendMessage(userInput, loginResponse.getToken());
                        if (sendMessageResponse == null) {
                            outputStream.println("Invalid response received. Exiting.");
                            return;
                        }

                        // TODO remove later
                        outputStream.println(sendMessageResponse.getId() + ": " + sendMessageResponse.getMessage());

                    } catch (HttpResponseException e1) {
                        outputStream.println("Status code " + e1.getStatusCode() + ". Exiting.");
                        return;
                    }

                }
            }
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

        return LoginResponse.checkNulls(loginResponse);
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

        return LogoutResponse.checkNulls(logoutResponse);
    }

    private @Nullable SendMessageResponse sendMessage(@NotNull String message, @NotNull String token)
            throws IOException, HttpResponseException {

        HttpPost httpPost = new HttpPost(messagesUri);

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

        return SendMessageResponse.checkNulls(sendMessageResponse);
    }

//    private @NotNull List<UserInfoResponse> usersList(@NotNull String token) { }

    private void fetchMessages(@Not)
}
