package ru.nsu.semenov.nsulabs.lab3.packets;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum PacketType {
    CONN_REQ(0),
    CONN_RESP(1),
    CONN_ACK(2),
    MSG_SND(3),
    MSG_ACK(4);

    PacketType(int value) {
        this.value = value;
    }

    public static Optional<PacketType> fromInt(int i) {
        return Optional.ofNullable(intToEnum.get(i));
    }

    public int getValue() {
        return value;
    }

    private final int value;
    private static final Map<Integer, PacketType> intToEnum =
            Stream.of(PacketType.values()).collect(Collectors.toMap(PacketType::getValue, x -> x));
}
