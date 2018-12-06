package ru.nsu.fit.semenov.portforwarder;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ForwardingData {
    private static final int BUF_SIZE = 512;

    private final SocketChannel clientSocketChannel;
    private final SocketChannel destSocketChannel;
    private final SelectionKey clientSelectionKey;
    private final SelectionKey destSelectionKey;
    private final ByteBuffer clientToDestBuffer = ByteBuffer.allocate(BUF_SIZE);
    private final ByteBuffer destToClientBuffer = ByteBuffer.allocate(BUF_SIZE);
    private boolean closedDestSocketChannel;
    private boolean closedClientSocketChannel;

    public ForwardingData(SocketChannel clientSocketChannel,
                          SocketChannel destSocketChannel,
                          SelectionKey clientSelectionKey,
                          SelectionKey destSelectionKey) {
        this.clientSocketChannel = clientSocketChannel;
        this.destSocketChannel = destSocketChannel;
        this.clientSelectionKey = clientSelectionKey;
        this.destSelectionKey = destSelectionKey;
        closedClientSocketChannel = false;
        closedDestSocketChannel = false;
    }

    public ByteBuffer getClientToDestBuffer() {
        return clientToDestBuffer;
    }

    public ByteBuffer getDestToClientBuffer() {
        return destToClientBuffer;
    }

    public SocketChannel getClientSocketChannel() {
        return clientSocketChannel;
    }

    public SocketChannel getDestSocketChannel() {
        return destSocketChannel;
    }

    public SelectionKey getClientSelectionKey() {
        return clientSelectionKey;
    }

    public SelectionKey getDestSelectionKey() {
        return destSelectionKey;
    }

    public boolean isClosedDestSocketChannel() {
        return closedDestSocketChannel;
    }

    public boolean isClosedClientSocketChannel() {
        return closedClientSocketChannel;
    }

    public void closeClientSocketChannel() {
        closedClientSocketChannel = true;
    }

    public void closeDestSocketChannel() {
        closedDestSocketChannel = true;
    }
}

