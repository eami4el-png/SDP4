package com.parking.space;

import java.util.UUID;

/**
 * Entity of subsystem B. The correlationId is an opaque reference given by the caller;
 * this subsystem does not know what it points to.
 */
public class SpaceReservation {

    public enum Status { NEW, RESERVED, RELEASED }

    private final String id;
    private final String correlationId;
    private int spotNumber;
    private Status status;

    public SpaceReservation(String id, String correlationId, int spotNumber, Status status) {
        this.id = id;
        this.correlationId = correlationId;
        this.spotNumber = spotNumber;
        this.status = status;
    }

    public static SpaceReservation create(String correlationId) {
        return new SpaceReservation(UUID.randomUUID().toString(), correlationId, 0, Status.NEW);
    }

    public void reserve(int spot) {
        if (status != Status.NEW) {
            throw new IllegalStateException("Reservation cannot go from " + status + " to RESERVED");
        }
        spotNumber = spot;
        status = Status.RESERVED;
    }

    public void release() {
        if (status != Status.RESERVED) {
            throw new IllegalStateException("Reservation cannot go from " + status + " to RELEASED");
        }
        status = Status.RELEASED;
    }

    public String getId() { return id; }
    public String getCorrelationId() { return correlationId; }
    public int getSpotNumber() { return spotNumber; }
    public Status getStatus() { return status; }
}
