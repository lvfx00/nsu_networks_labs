package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public final class TextMessage implements Message {
    private TextMessage(@NotNull SocketAddress senderAddress,
                        @NotNull UUID uuid,
                        @NotNull String name,
                        @NotNull String text) {
        this.senderAddress = senderAddress;
        this.uuid = uuid;
        this.name = name;
        this.text = text;
    }

    public static @NotNull TextMessage newInstance(@NotNull SocketAddress senderAddress,
                                                   @NotNull UUID uuid,
                                                   @NotNull String name,
                                                   @NotNull String text) {
        return new TextMessage(senderAddress, uuid, name, text);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.TEXT;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    @Override
    public @NotNull SocketAddress getAddress() {
        return senderAddress;
    }

    public @NotNull String getText() {
        return text;
    }

    public @NotNull String getName() {
        return name;
    }

    private final SocketAddress senderAddress;
    private final UUID uuid;
    private final String name;
    private final String text;
}
