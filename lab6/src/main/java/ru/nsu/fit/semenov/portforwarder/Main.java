package ru.nsu.fit.semenov.portforwarder;

public class Main {
    public static void main(String[] args) {
        PortForwarder portForwarder = new PortForwarder(10080, "fit.ippolitov.me", 80);
        portForwarder.run();
    }
}
