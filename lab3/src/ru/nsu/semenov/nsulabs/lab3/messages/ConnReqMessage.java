package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public class ConnReqMessage implements Message {
    private ConnReqMessage(SocketAddress senderAddress) {
        this.senderAddress = senderAddress;
    }

    public static @NotNull ConnReqMessage newInstance(@NotNull SocketAddress senderAddress) {
        return new ConnReqMessage(senderAddress);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.CONN_REQ;
    }

    @Override
    public @NotNull
    SocketAddress getAddress() {
        return senderAddress;
    }

    private final SocketAddress senderAddress;
}
