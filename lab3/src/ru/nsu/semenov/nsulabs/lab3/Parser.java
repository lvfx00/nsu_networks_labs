package ru.nsu.semenov.nsulabs.lab3;

import com.sun.istack.internal.NotNull;
import ru.nsu.semenov.nsulabs.lab3.packets.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class Parser {
    private static final Charset encodingCharset = StandardCharsets.UTF_8;

    private Parser() {}

    public static Packet parse(ByteBuffer buffer) throws IllegalArgumentException, BufferUnderflowException {
        int msgTypeValue = buffer.getInt();
        Optional<PacketType> optionalPacketType = PacketType.fromInt(msgTypeValue);
        if (!optionalPacketType.isPresent()) {
            throw new IllegalArgumentException("Invalid packet type code");
        }
        PacketType packetType = optionalPacketType.get();

        switch (packetType) {
            case CONN_REQ:
                return ConnectionRequestPacket.newInstance();
            case CONN_RESP:
                return ConnectionResponsePacket.newInstance();
            case CONN_ACK:
                return ConnectionAcknowledgePacket.newInstance();
            case MSG_SND: {
                long mostSignificantBits = buffer.getLong();
                long leastSignificantBits = buffer.getLong();
                UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                int nameLength = buffer.getInt();
                byte[] nameBuffer = new byte[nameLength];
                buffer.get(nameBuffer);
                String name = new String(nameBuffer, encodingCharset);

                int dataLength = buffer.getInt();
                byte[] dataBuffer = new byte[dataLength];
                buffer.get(dataBuffer);
                String data = new String(dataBuffer, encodingCharset);

                return MessageSendPacket.newInstance(messageUuid, name, data);
            }
            case MSG_ACK: {
                long mostSignificantBits = buffer.getLong();
                long leastSignificantBits = buffer.getLong();
                UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                return MessageAcknowledgePacket.newInstance(messageUuid);
            }
            default:
                throw new IllegalArgumentException("Can't resolve packet's type");
        }
    }

    public static @NotNull ByteBuffer extractData(@NotNull Packet packet) {
        switch (packet.getPacketType()) {
            case MSG_SND: {
                MessageSendPacket messageSendPacket = (MessageSendPacket) packet;

                byte[] encodedName = messageSendPacket.getName().getBytes(encodingCharset);
                byte[] encodedData = messageSendPacket.getData().getBytes(encodingCharset);

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3 + Long.BYTES * 2
                        + encodedName.length + encodedData.length);

                buffer.putInt(messageSendPacket.getPacketType().getValue());

                buffer.putLong(messageSendPacket.getMessageUuid().getMostSignificantBits());
                buffer.putLong(messageSendPacket.getMessageUuid().getLeastSignificantBits());

                buffer.putInt(encodedName.length);
                buffer.put(encodedName);

                buffer.putInt(encodedData.length);
                buffer.put(encodedData);

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case MSG_ACK: {
                MessageAcknowledgePacket messageAcknowledgePacket = (MessageAcknowledgePacket) packet;

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 2);

                buffer.putInt(messageAcknowledgePacket.getPacketType().getValue());

                buffer.putLong(messageAcknowledgePacket.getMessageUuid().getMostSignificantBits());
                buffer.putLong(messageAcknowledgePacket.getMessageUuid().getLeastSignificantBits());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONN_REQ: {
                ConnectionRequestPacket connectionRequestPacket = (ConnectionRequestPacket) packet;
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
                buffer.putInt(connectionRequestPacket.getPacketType().getValue());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONN_RESP: {
                ConnectionResponsePacket connectionResponsePacket = (ConnectionResponsePacket) packet;
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
                buffer.putInt(connectionResponsePacket.getPacketType().getValue());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONN_ACK: {
                ConnectionAcknowledgePacket connectionAcknowledgePacket = (ConnectionAcknowledgePacket) packet;
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
                buffer.putInt(connectionAcknowledgePacket.getPacketType().getValue());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            default:
                throw new IllegalArgumentException("Can't resolve packet's type");
        }
    }
}
