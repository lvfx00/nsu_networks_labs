package ru.nsu.semenov.nsulabs.lab3.messages;

import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;

abstract class AbstractMessage implements Message {
    AbstractMessage(@NotNull SocketAddress address) {
        this.address = address;
    }

    @Override
    public final @NotNull SocketAddress getDestAddress() {
        return address;
    }

    private final SocketAddress address;
}
