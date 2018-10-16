package ru.nsu.semenov.nsulabs.lab3.packets;

import com.sun.istack.internal.NotNull;

import java.util.UUID;

public class MessageAcknowledgePacket implements Packet {
    @Override
    public PacketType getPacketType() {
        return PacketType.MSG_ACK;
    }

    private MessageAcknowledgePacket(@NotNull UUID messageUuid) {
        this.messageUuid = messageUuid;
    }

    public static MessageAcknowledgePacket newInstance(@NotNull UUID messageUuid) {
        return new MessageAcknowledgePacket(messageUuid);
    }

    public @NotNull UUID getMessageUuid() {
        return messageUuid;
    }

    private final UUID messageUuid;
}
