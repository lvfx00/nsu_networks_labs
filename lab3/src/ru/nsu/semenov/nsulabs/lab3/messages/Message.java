package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

public interface Message {
    @NotNull MessageType getMessageType();

    @NotNull SocketAddress getAddress();
}
