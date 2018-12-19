package ru.nsu.fit.semenov.socksproxy;

import org.jetbrains.annotations.NotNull;

public class SocketChannelRef {
    private final SocksClient socksClient;
    private final SocketChannelSide socketChannelSide;

    public SocketChannelRef(SocksClient socksClient, @NotNull SocketChannelRef.SocketChannelSide socketChannelSide) {
        this.socksClient = socksClient;
        this.socketChannelSide = socketChannelSide;
    }

    public @NotNull SocksClient getSocksClient() {
        return socksClient;
    }

    public @NotNull SocketChannelRef.SocketChannelSide getSocketChannelSide() {
        return socketChannelSide;
    }

    enum SocketChannelSide {
        CLIENT,
        DESTINATION;
    }
}
