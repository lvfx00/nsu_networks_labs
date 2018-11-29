package ru.nsu.fit.semenov.restchat.user;

public enum UserOnlineState {
    ONLINE("true"),
    OFFLINE("false"),
    TIMED_OUT("null");

    UserOnlineState(String s) {
        stringRepresentation = s;
    }

    @Override
    public String toString() {
        return stringRepresentation;
    }

    private final String stringRepresentation;
}
