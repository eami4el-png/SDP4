package com.parking.payment;

import java.util.UUID;

/**
 * Entity of subsystem C. The correlationId is an opaque reference given by the caller;
 * this subsystem does not know what it points to.
 */
public class PaymentSession {

    public enum Status { NEW, ACTIVE, FAILED }

    private final String id;
    private final String correlationId;
    private final int hours;
    private int fee;
    private Status status;

    public PaymentSession(String id, String correlationId, int hours, int fee, Status status) {
        this.id = id;
        this.correlationId = correlationId;
        this.hours = hours;
        this.fee = fee;
        this.status = status;
    }

    public static PaymentSession create(String correlationId, int hours) {
        return new PaymentSession(UUID.randomUUID().toString(), correlationId, hours, 0, Status.NEW);
    }

    public void activate(int fee) {
        if (status != Status.NEW) {
            throw new IllegalStateException("Payment cannot go from " + status + " to ACTIVE");
        }
        this.fee = fee;
        status = Status.ACTIVE;
    }

    public void fail() {
        if (status == Status.FAILED) {
            throw new IllegalStateException("Payment is already FAILED");
        }
        status = Status.FAILED;
    }

    public String getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public int getHours() { return hours; }
    public int getFee() { return fee; }
    public Status getStatus() { return status; }
}
