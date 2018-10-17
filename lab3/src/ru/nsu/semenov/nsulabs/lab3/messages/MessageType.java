package ru.nsu.semenov.nsulabs.lab3.messages;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum MessageType {
    CONN_REQ(0),
    CONN_RESP(1),
    CONN_ACK(2),
    TEXT(3),
    TEXT_ACK(4);

    MessageType(int value) {
        this.value = value;
    }

    public static Optional<MessageType> fromInt(int i) {
        return Optional.ofNullable(intToEnum.get(i));
    }

    public int getValue() {
        return value;
    }

    private final int value;
    private static final Map<Integer, MessageType> intToEnum =
            Stream.of(MessageType.values()).collect(Collectors.toMap(MessageType::getValue, x -> x));
}
