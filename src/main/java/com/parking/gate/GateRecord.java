package com.parking.gate;

import java.util.UUID;

/** Entity of subsystem A. It enforces its own legal status transitions. */
public class GateRecord {

    public enum Status { NEW, ENTERED, CANCELLED }

    private final String id;
    private final String plateNumber;
    private Status status;

    public GateRecord(String id, String plateNumber, Status status) {
        this.id = id;
        this.plateNumber = plateNumber;
        this.status = status;
    }

    public static GateRecord create(String plateNumber) {
        return new GateRecord(UUID.randomUUID().toString(), plateNumber, Status.NEW);
    }

    public void enter() {
        transitionTo(Status.ENTERED);
    }

    public void cancel() {
        transitionTo(Status.CANCELLED);
    }

    private void transitionTo(Status next) {
        boolean legal = switch (status) {
            case NEW -> next == Status.ENTERED || next == Status.CANCELLED;
            case ENTERED -> next == Status.CANCELLED;
            case CANCELLED -> false;
        };
        if (!legal) {
            throw new IllegalStateException("Gate record cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public String getId() { return id; }
    public String getPlateNumber() { return plateNumber; }
    public Status getStatus() { return status; }
}
