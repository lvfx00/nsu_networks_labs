package ru.nsu.fit.semenov.restchatwebsocket.client;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        RestChatClient client = new RestChatClient("localhost",
                8080,
                new Scanner(System.in),
                System.out);

        client.run();
    }
}
