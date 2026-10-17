package com.parking.facade;

/** Thrown by the facade when a request is rejected before anything was committed. */
public class ParkingRejectedException extends RuntimeException {
    public ParkingRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
