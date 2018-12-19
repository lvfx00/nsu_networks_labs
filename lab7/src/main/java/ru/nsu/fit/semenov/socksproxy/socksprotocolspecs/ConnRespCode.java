package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

public enum ConnRespCode {
    REQUEST_GRANTED((byte)0x00),
    HOST_UNREACHABLE((byte)0x04),
    CMD_NOT_SUPPORTEED((byte)0x07),
    ADDR_TYPE_NOT_SUPPORTED((byte)0x08);

    private final byte value;

    ConnRespCode(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
