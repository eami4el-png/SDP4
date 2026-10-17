package com.parking.payment;

public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
