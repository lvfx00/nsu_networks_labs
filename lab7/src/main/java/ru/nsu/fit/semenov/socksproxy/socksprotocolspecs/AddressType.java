package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum AddressType {
    IPV4_ADDRESS((byte)0x01),
    DOMAIN_NAME((byte)0x03),
    IPV6_ADDRESS((byte)0x04);

    private final byte value;

    private static final Map<Byte, AddressType> valuesToCommands = Stream.of(values())
            .collect(toMap(AddressType::getValue, e -> e));

    public static @Nullable AddressType getByValue(byte value) {
        return valuesToCommands.get(value);
    }

    AddressType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
