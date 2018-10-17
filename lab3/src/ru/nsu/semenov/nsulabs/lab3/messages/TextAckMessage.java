package ru.nsu.semenov.nsulabs.lab3.messages;


import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public final class TextAckMessage extends AbstractMessage {
    public TextAckMessage(@NotNull SocketAddress address, @NotNull UUID uuid) {
        super(address);
        this.uuid = uuid;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.TEXT_ACK;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    private final UUID uuid;
}
