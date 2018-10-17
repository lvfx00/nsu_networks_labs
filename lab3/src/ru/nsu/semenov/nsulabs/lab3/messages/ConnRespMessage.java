package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public final class ConnRespMessage extends AbstractMessage {
    public ConnRespMessage(@NotNull SocketAddress address) {
        super(address);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.CONN_RESP;
    }
}
