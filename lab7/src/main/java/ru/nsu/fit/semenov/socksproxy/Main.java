package ru.nsu.fit.semenov.socksproxy;

public class Main {
    public static void main(String[] args) {
        SocksProxy socksProxy = new SocksProxy(10080);
        socksProxy.run();
    }
}
