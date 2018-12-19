package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public enum ConnReqCommand {
    ESTABLISH_STREAM_CONNECTION((byte)0x01),
    ESTABLISH_PORT_BINDING((byte)0x02),
    ASSOCIATE_UDP_PORT((byte)0x03);

    private final byte value;

    private static final Map<Byte, ConnReqCommand> valuesToCommands = Stream.of(values())
            .collect(toMap(ConnReqCommand::getValue, e -> e));

    public static ConnReqCommand getByValue(byte value) {
        return valuesToCommands.get(value);
    }

    ConnReqCommand(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
