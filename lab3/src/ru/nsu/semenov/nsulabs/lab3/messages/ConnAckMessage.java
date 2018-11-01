package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public class ConnAckMessage implements Message {
    private ConnAckMessage(SocketAddress senderAddress) {
        this.senderAddress = senderAddress;
    }

    public static @NotNull ConnAckMessage newInstance(@NotNull SocketAddress senderAddress) {
        return new ConnAckMessage(senderAddress);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.CONN_ACK;
    }

    @Override
    public @NotNull SocketAddress getAddress() {
        return senderAddress;

    }

    private final SocketAddress senderAddress;
}
