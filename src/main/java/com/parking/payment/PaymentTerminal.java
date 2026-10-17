package com.parking.payment;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Subsystem C entry point: the payment terminal. */
public class PaymentTerminal {
    private final PaymentRepository repository;
    private final OverstayPolicy policy;
    private boolean online = true;

    public PaymentTerminal(PaymentRepository repository, OverstayPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public PaymentSession authorize(String correlationId, int hours) {
        PaymentSession session = PaymentSession.create(correlationId, hours);
        repository.save(session);
        try {
            if (!online) {
                throw new PaymentDeclinedException("Payment terminal is offline");
            }
            session.activate(policy.feeFor(hours));
        } catch (PaymentDeclinedException e) {
            session.fail();
            repository.save(session);
            throw e;
        }
        repository.save(session);
        return session;
    }

    public void voidSession(String sessionId) {
        PaymentSession session = repository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("No payment session " + sessionId));
        session.fail();
        repository.save(session);
    }

    public Optional<PaymentSession> find(String sessionId) {
        return repository.findById(sessionId);
    }

    public Optional<PaymentSession> findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId);
    }

    public List<PaymentSession> all() {
        return repository.findAll();
    }
}
