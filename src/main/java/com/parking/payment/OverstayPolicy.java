package com.parking.payment;

/** Pure business logic: maximum allowed stay and the fee for a stay. */
public class OverstayPolicy {
    public static final int MAX_HOURS = 24;
    public static final int RATE_PER_HOUR = 200;

    public int feeFor(int hours) {
        if (hours < 1) {
            throw new PaymentDeclinedException("Stay must be at least 1 hour");
        }
        if (hours > MAX_HOURS) {
            throw new PaymentDeclinedException(
                    "Stay of " + hours + " h exceeds the " + MAX_HOURS + " h limit (overstay)");
        }
        return hours * RATE_PER_HOUR;
    }
}
