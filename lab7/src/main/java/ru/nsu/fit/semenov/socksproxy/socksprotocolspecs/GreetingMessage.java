package ru.nsu.fit.semenov.socksproxy.socksprotocolspecs;

import org.jetbrains.annotations.NotNull;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class GreetingMessage {
    private final byte socksVersion;
    private final Set<AuthMethod> authMethods;

    public static @NotNull GreetingMessage fromByteBuffer(@NotNull ByteBuffer buffer)
            throws BufferUnderflowException, IllegalArgumentException {
        buffer.flip();

        final byte socksVersion = buffer.get();

        final byte authMethodsNum = buffer.get();
        checkArgument(authMethodsNum >= 0);

        final Set<AuthMethod> authMethods = new HashSet<>();

        for (int i = 0; i < authMethodsNum; ++i) {
            AuthMethod authMethod = AuthMethod.getByValue(buffer.get());

            if (authMethod != null) {
                authMethods.add(authMethod);
            }
        }

        buffer.compact();
        return new GreetingMessage(socksVersion, authMethods);
    }

    public GreetingMessage(byte socksVersion, Set<AuthMethod> authMethods) {
        this.socksVersion = socksVersion;
        this.authMethods = authMethods;
    }

    public byte getSocksVersion() {
        return socksVersion;
    }

    public Set<AuthMethod> getAuthMethods() {
        return Collections.unmodifiableSet(authMethods);
    }

    public boolean hasAuthMethod(AuthMethod method) {
        return authMethods.contains(method);
    }
}
