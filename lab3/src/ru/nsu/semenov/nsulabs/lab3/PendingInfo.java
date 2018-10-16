package ru.nsu.semenov.nsulabs.lab3;

import com.sun.istack.internal.NotNull;

import java.time.Clock;
import java.time.Instant;

public class PendingInfo {
    private PendingInfo(int resendingCount, @NotNull Instant lastSendTime) {
        this.resendingCount = resendingCount;
        this.lastSendTime = lastSendTime;
    }

    public static @NotNull PendingInfo newInstance() {
        return new PendingInfo(0, Clock.systemUTC().instant());
    }

    public int getResendingCount() {
        return resendingCount;
    }

    public @NotNull Instant getLastSendTime() {
        return lastSendTime;
    }

    public void incrementResendingCount() {
        if (resendingCount < Integer.MAX_VALUE)
            resendingCount++;
    }

    public void updateLastTime() {
        lastSendTime = Clock.systemUTC().instant();
    }

    private int resendingCount;
    private Instant lastSendTime;
}
