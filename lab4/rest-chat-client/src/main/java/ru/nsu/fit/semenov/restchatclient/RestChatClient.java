package ru.nsu.fit.semenov.restchatclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.net.URI;
import java.net.URISyntaxException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class RestChatClient implements Runnable {
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final String hostname;
    private final int port;

    private final URI loginUri;
    private final URI logoutUri;


    public RestChatClient(String hostname, int port) {
        this.hostname = checkNotNull(hostname);
        checkArgument(port > 0 && port < 65536);
        this.port = port;

        try {
            loginUri = new URI("http", null, hostname, port, "/login", null, null);
            logoutUri = new URI("http", null, hostname, port, "/logout", null, null);

        } catch (URISyntaxException e) {
            System.err.println(e.getMessage());
            throw new IllegalArgumentException("Invalid hostname or port specified");
        }
    }

    @Override
    public void run() {
        login();

    }

    private void login() {
        HttpPost httpPost = new HttpPost(loginUri);

        Lo

    }

    private void logout() {

    }
}
