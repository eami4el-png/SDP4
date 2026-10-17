package com.parking.space;

public class SpaceUnavailableException extends RuntimeException {
    public SpaceUnavailableException(String message) {
        super(message);
    }
}
