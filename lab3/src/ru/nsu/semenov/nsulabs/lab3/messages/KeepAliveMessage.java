package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public class KeepAliveMessage implements Message {
    private KeepAliveMessage(SocketAddress senderAddress) {
        this.senderAddress = senderAddress;
    }

    public static @NotNull
    KeepAliveMessage newInstance(@NotNull SocketAddress senderAddress) {
        return new KeepAliveMessage(senderAddress);
    }

    @Override
    public @NotNull
    MessageType getMessageType() {
        return MessageType.KEEP_ALIVE;
    }

    @Override
    public @NotNull
    SocketAddress getAddress() {
        return senderAddress;

    }

    private final SocketAddress senderAddress;
}
