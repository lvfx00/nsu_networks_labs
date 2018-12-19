package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

import org.jetbrains.annotations.NotNull;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static ru.nsu.fit.semenov.socksproxy.socksprotocolspecs.AddressType.IPV4_ADDRESS;

public class ConnectionRequest {
    private static final byte RESERVED = (byte) 0x00;
    private static final int IPV4_ADDRESS_LEN = 4;
    private static final int IPV6_ADDRESS_LEN = 16;

    private final byte socksVersion;
    private final ConnReqCommand command;
    private final AddressType addressType;
    private final Object address;
    private final int port;

    public static @NotNull ConnectionRequest fromByteBuffer(ByteBuffer buffer)
            throws BufferUnderflowException, IllegalArgumentException {
        buffer.flip();

        final byte socksVersion = buffer.get();

        final ConnReqCommand command = ConnReqCommand.getByValue(buffer.get());
        if (command == null) {
            throw new IllegalArgumentException("Invalid command specified");
        }

        byte reserved = buffer.get();
        checkArgument(reserved == RESERVED);

        final AddressType addressType = AddressType.getByValue(buffer.get());
        if (addressType == null) {
            throw new IllegalArgumentException("Invalid address type specified");
        }

        final Object address;

        switch (addressType) {
            case IPV4_ADDRESS:
            case IPV6_ADDRESS: {
                int size = (addressType == IPV4_ADDRESS) ? IPV4_ADDRESS_LEN : IPV6_ADDRESS_LEN;
                byte[] rawInetAddress = new byte[size];
                buffer.get(rawInetAddress);

                try {
                    address = InetAddress.getByAddress(rawInetAddress);
                } catch (UnknownHostException uhe) {
                    throw new IllegalArgumentException("Invalid address received");
                }

                break;
            }
            case DOMAIN_NAME: {
                byte domainNameLength = buffer.get();
                checkArgument(domainNameLength > 0);

                byte[] domainName = new byte[domainNameLength];
                buffer.get(domainName);

                address = new String(domainName, UTF_8);
                break;
            }
            default:
                throw new IllegalArgumentException("Invalid address type number");
        }

        int port = buffer.getShort();

        buffer.compact();
        return new ConnectionRequest(socksVersion, command, addressType, address, port);
    }

    public ConnectionRequest(byte socksVersion,
                             ConnReqCommand command,
                             AddressType addressType,
                             Object address,
                             int port) {
        this.socksVersion = socksVersion;
        this.command = command;
        this.addressType = addressType;

        // TODO change to type stored in enum
        switch (addressType) {
            case IPV4_ADDRESS:
                if (!(address instanceof Inet4Address))
                    throw new IllegalArgumentException("Invalid address for specified address type");
                break;
            case DOMAIN_NAME:
                if (!(address instanceof String))
                    throw new IllegalArgumentException("Invalid address for specified address type");
                break;
            case IPV6_ADDRESS:
                if (!(address instanceof Inet6Address))
                    throw new IllegalArgumentException("Invalid address for specified address type");
                break;
            default:
                throw new AssertionError("Invalid AddressType");
        }

        this.address = address;
        this.port = port;
    }


    public byte getSocksVersion() {
        return socksVersion;
    }

    public ConnReqCommand getCommandNumber() {
        return command;
    }

    public AddressType getAddressType() {
        return addressType;
    }

    public Object getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}
