package ru.nsu.fit.semenov.socksproxy;

public enum SocksClientState {
    RECV_INIT_GREETING,
    SEND_GREETING_RESP,
    RECV_CONN_REQ,
    RESOLVING_ADDRESS,
    CONNECTING_TO_DEST,
    SEND_CONN_RESP,
    ACTIVE,
    CLOSED
}
