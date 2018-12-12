package ru.nsu.fit.semenov.restchatwebsocket.server;

public class Main {
    public static void main(String[] args) {
        RestChatServer server = new RestChatServer(8080);
        server.run();
    }
}
