package ru.nsu.semenov.nsulabs.lab3.messages;


import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public class TextAckMessage implements Message {
    private TextAckMessage(SocketAddress senderAddress, UUID uuid) {
        this.uuid = uuid;
        this.senderAddress = senderAddress;
    }

    public static @NotNull TextAckMessage newInstance(@NotNull SocketAddress senderAddress,
                                                      @NotNull UUID uuid) {
        return new TextAckMessage(senderAddress, uuid);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.TEXT_ACK;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    @Override
    public @NotNull SocketAddress getAddress() {
        return senderAddress;
    }

    private final SocketAddress senderAddress;
    private final UUID uuid;
}
