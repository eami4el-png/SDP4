package com.parking.facade;

import java.util.UUID;

/** Persisted progress of one A -> B -> C chain. */
public class ChainRecord {

    public enum Status { PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED }

    private final String id;
    private final String plateNumber;
    private final String gateId;
    private final int hours;
    private String reservationId;
    private String paymentId;
    private String failureReason;
    private Status status;

    ChainRecord(String id, String plateNumber, String gateId, int hours, String reservationId,
                String paymentId, Status status, String failureReason) {
        this.id = id;
        this.plateNumber = plateNumber;
        this.gateId = gateId;
        this.hours = hours;
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        this.status = status;
        this.failureReason = clean(failureReason);
    }

    static ChainRecord start(String plateNumber, String gateId, int hours) {
        return new ChainRecord(UUID.randomUUID().toString(), plateNumber, gateId, hours,
                "", "", Status.PENDING_B, "");
    }

    void awaitPayment(String reservationId) {
        require(status == Status.PENDING_B, Status.PENDING_C);
        this.reservationId = reservationId;
        status = Status.PENDING_C;
    }

    void complete(String reservationId, String paymentId) {
        require(status == Status.PENDING_B || status == Status.PENDING_C, Status.COMPLETED);
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        status = Status.COMPLETED;
    }

    void compensating(String reason) {
        require(status != Status.COMPENSATED, Status.COMPENSATING);
        if (failureReason.isEmpty()) {
            failureReason = clean(reason);
        }
        status = Status.COMPENSATING;
    }

    void compensated() {
        require(status == Status.COMPENSATING, Status.COMPENSATED);
        status = Status.COMPENSATED;
    }

    private void require(boolean legal, Status next) {
        if (!legal) {
            throw new IllegalStateException("Chain cannot go from " + status + " to " + next);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    public String getId() { return id; }
    public String getPlateNumber() { return plateNumber; }
    public String getGateId() { return gateId; }
    public int getHours() { return hours; }
    public String getReservationId() { return reservationId; }
    public String getPaymentId() { return paymentId; }
    public String getFailureReason() { return failureReason; }
    public Status getStatus() { return status; }
}
