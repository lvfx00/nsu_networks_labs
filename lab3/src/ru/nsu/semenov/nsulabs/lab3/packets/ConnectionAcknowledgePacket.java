package ru.nsu.semenov.nsulabs.lab3.packets;

public class ConnectionAcknowledgePacket implements Packet {
    @Override
    public PacketType getPacketType() {
        return PacketType.CONN_ACK;
    }

    private ConnectionAcknowledgePacket() {}

    public static ConnectionAcknowledgePacket newInstance() {
        return instance;
    }

    private static final ConnectionAcknowledgePacket instance = new ConnectionAcknowledgePacket();
}
