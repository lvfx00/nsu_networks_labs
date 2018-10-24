package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public class ConnectionMessage implements Message {
    private ConnectionMessage(SocketAddress senderAddress, UUID uuid) {
        this.uuid = uuid;
        this.senderAddress = senderAddress;
    }

    public static @NotNull ConnectionMessage newInstance(@NotNull SocketAddress senderAddress, @NotNull UUID uuid) {
        return new ConnectionMessage(senderAddress, uuid);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.CONNECTION;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    @Override
    public @NotNull
    SocketAddress getDestAddress() {
        return senderAddress;
    }

    private final UUID uuid;
    private final SocketAddress senderAddress;
}
