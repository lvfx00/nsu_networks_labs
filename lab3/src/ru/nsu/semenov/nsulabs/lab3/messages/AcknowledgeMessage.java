package ru.nsu.semenov.nsulabs.lab3.messages;


import org.jetbrains.annotations.NotNull;

import java.net.SocketAddress;
import java.util.UUID;

public class AcknowledgeMessage implements Message {
    private AcknowledgeMessage(SocketAddress senderAddress, UUID uuid, UUID acknowledgeUuid) {
        this.uuid = uuid;
        this.senderAddress = senderAddress;
        this.acknowledgeUuid = acknowledgeUuid;
    }

    public static @NotNull AcknowledgeMessage newInstance(@NotNull SocketAddress senderAddress,
                                                          @NotNull UUID uuid,
                                                          @NotNull UUID acknowledgeUuid) {
        return new AcknowledgeMessage(senderAddress, uuid, acknowledgeUuid);
    }

    @Override
    public @NotNull MessageType getMessageType() {
        return MessageType.ACKNOWLEDGE;
    }

    @Override
    public @NotNull UUID getUuid() {
        return uuid;
    }

    @Override
    public @NotNull SocketAddress getDestAddress() {
        return senderAddress;
    }

    public @NotNull UUID getAcknowledgeUuid() {
        return acknowledgeUuid;
    }

    private final UUID acknowledgeUuid;
    private final SocketAddress senderAddress;
    private final UUID uuid;
}
