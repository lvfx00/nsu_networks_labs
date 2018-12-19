package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConnectionResponse {
    private static final byte RESERVED = (byte) 0x00;

    private final byte socksVersion;
    private final ConnRespCode responseCode;
    private final AddressType addressType;
    private final Object address;
    private final int port;


    public ConnectionResponse(byte socksVersion,
                              ConnRespCode responseCode,
                              AddressType addressType,
                              Object address,
                              int port) {
        this.socksVersion = socksVersion;
        this.responseCode = responseCode;
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

    public byte[] toByteArray() {
        int size;
        switch (addressType) {
            case IPV4_ADDRESS:
                size = 10;
                break;
            case IPV6_ADDRESS:
                size = 22;
                break;
            case DOMAIN_NAME:
                size = 7 + ((String)address).length();
                break;
            default:
                throw new AssertionError("Invalid address type");
        }

        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.put(socksVersion);
        buffer.put(responseCode.getValue());
        buffer.put(RESERVED);
        buffer.put(addressType.getValue());

        switch (addressType) {
            case IPV4_ADDRESS:
                buffer.put(((Inet4Address)address).getAddress());
                break;
            case IPV6_ADDRESS:
                buffer.put(((Inet6Address)address).getAddress());
                break;
            case DOMAIN_NAME:
                buffer.put(((String)address).getBytes(UTF_8));
                break;
            default:
                throw new AssertionError("Invalid address type");
        }

        buffer.putShort((short) port);
        return buffer.array();
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public ConnRespCode getCommandNumber() {
        return responseCode;
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
