package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public final class TextMessage extends AbstractMessage {
    public TextMessage(@NotNull SocketAddress address,
                        @NotNull UUID uuid,
                        @NotNull String name,
                        @NotNull String data) {
        super(address);
        this.uuid = uuid;
        this.name = name;
        this.data = data;
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.TEXT;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    public @NotNull String getData() {
        return data;
    }

    public @NotNull String getName() {
        return name;
    }

    private final UUID uuid;
    private final String name;
    private final String data;
}
