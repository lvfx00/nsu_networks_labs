package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public interface Message {
    @NotNull MessageType getMessageType();

    @NotNull UUID getUuid();

    @NotNull SocketAddress getDestAddress();
}
