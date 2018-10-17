package ru.nsu.semenov.nsulabs.lab3;

import com.sun.istack.internal.NotNull;
import ru.nsu.semenov.nsulabs.lab3.messages.*;

import java.net.SocketAddress;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

public class Parser {
    private static final Charset encodingCharset = StandardCharsets.UTF_8;

    private Parser() {}

    public static Message parse(ByteBuffer buffer, ParserContext context)
            throws IllegalArgumentException, BufferUnderflowException {

        int msgTypeValue = buffer.getInt();
        Optional<MessageType> optionalPacketType = MessageType.fromInt(msgTypeValue);
        if (!optionalPacketType.isPresent()) {
            throw new IllegalArgumentException("Invalid packet type code");
        }
        MessageType messageType = optionalPacketType.get();

        switch (messageType) {
            case CONN_REQ:
                return new ConnReqMessage(context.getAddress());
            case CONN_RESP:
                return new ConnRespMessage(context.getAddress());
            case CONN_ACK:
                return new ConnAckMessage(context.getAddress());
            case TEXT: {
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

                return new TextMessage(context.getAddress(), messageUuid, name, data);
            }
            case TEXT_ACK: {
                long mostSignificantBits = buffer.getLong();
                long leastSignificantBits = buffer.getLong();
                UUID messageUuid = new UUID(mostSignificantBits, leastSignificantBits);

                return new TextAckMessage(context.getAddress(), messageUuid);
            }
            default:
                throw new IllegalArgumentException("Can't resolve packet's type");
        }
    }

    public static @NotNull ByteBuffer extractData(@NotNull Message message) {
        switch (message.getMessageType()) {
            case TEXT: {
                TextMessage textMessage = (TextMessage) message;

                byte[] encodedName = textMessage.getName().getBytes(encodingCharset);
                byte[] encodedData = textMessage.getData().getBytes(encodingCharset);

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES * 3 + Long.BYTES * 2
                        + encodedName.length + encodedData.length);

                buffer.putInt(textMessage.getMessageType().getValue());

                buffer.putLong(textMessage.getUuid().getMostSignificantBits());
                buffer.putLong(textMessage.getUuid().getLeastSignificantBits());

                buffer.putInt(encodedName.length);
                buffer.put(encodedName);

                buffer.putInt(encodedData.length);
                buffer.put(encodedData);

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case TEXT_ACK: {
                TextAckMessage textAckMessage = (TextAckMessage) message;

                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES * 2);

                buffer.putInt(textAckMessage.getMessageType().getValue());

                buffer.putLong(textAckMessage.getUuid().getMostSignificantBits());
                buffer.putLong(textAckMessage.getUuid().getLeastSignificantBits());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            case CONN_REQ:
            case CONN_RESP:
            case CONN_ACK: {
                ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
                buffer.putInt(message.getMessageType().getValue());

                buffer.rewind();
                return buffer.asReadOnlyBuffer();
            }
            default:
                throw new IllegalArgumentException("Can't resolve packet's type");
        }
    }

    public static class ParserContext {
        public @NotNull SocketAddress getAddress() {
            return address;
        }

        public ParserContext(@NotNull SocketAddress address) {
            this.address = address;
        }

        private final SocketAddress address;
    }
}
