package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

public class GreetingResponse {
    private final byte socksVersion;
    private final AuthMethod chosenAuthMethod;

    public GreetingResponse(byte socksVersion, AuthMethod chosenAuthMethod) {
        this.socksVersion = socksVersion;
        this.chosenAuthMethod = chosenAuthMethod;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public AuthMethod getChosenAuthMethod() {
        return chosenAuthMethod;
    }

    public byte[] toByteArray() {
        byte[] byteArray = new byte[2];
        byteArray[0] = socksVersion;
        byteArray[1] = chosenAuthMethod.getValue();
        return byteArray;
    }
}
