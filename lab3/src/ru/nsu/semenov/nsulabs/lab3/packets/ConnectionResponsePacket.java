package ru.nsu.semenov.nsulabs.lab3.packets;

public class ConnectionResponsePacket implements Packet {
    @Override
    public PacketType getPacketType() {
        return PacketType.CONN_RESP;
    }

    private ConnectionResponsePacket() {}

    public static ConnectionResponsePacket newInstance() {
        return instance;
    }

    private static final ConnectionResponsePacket instance = new ConnectionResponsePacket();
}
