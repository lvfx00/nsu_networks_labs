package ru.nsu.fit.semenov.portforwarder;

import org.jetbrains.annotations.NotNull;

public class SocketChannelRef {
    private final ForwardingData forwardingData;
    private final SocketChannelSide socketChannelSide;

    public SocketChannelRef(ForwardingData forwardingData, @NotNull SocketChannelRef.SocketChannelSide socketChannelSide) {
        this.forwardingData = forwardingData;
        this.socketChannelSide = socketChannelSide;
    }

    public @NotNull ForwardingData getForwardingData() {
        return forwardingData;
    }

    public @NotNull SocketChannelRef.SocketChannelSide getSocketChannelSide() {
        return socketChannelSide;
    }

    enum SocketChannelSide {
        CLIENT,
        DESTINATION;
    }
}
