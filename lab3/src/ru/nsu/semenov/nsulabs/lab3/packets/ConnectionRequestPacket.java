package ru.nsu.semenov.nsulabs.lab3.packets;

public class ConnectionRequestPacket implements Packet {
    @Override
    public PacketType getPacketType() {
        return PacketType.CONN_REQ;
    }

    private ConnectionRequestPacket() {}

    public static ConnectionRequestPacket newInstance() {
        return instance;
    }

    private static final ConnectionRequestPacket instance = new ConnectionRequestPacket();
}
