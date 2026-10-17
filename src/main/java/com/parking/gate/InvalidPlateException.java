package com.parking.gate;

public class InvalidPlateException extends RuntimeException {
    public InvalidPlateException(String message) {
        super(message);
    }
}
