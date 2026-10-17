package com.parking.facade;

import com.parking.gate.GateController;
import com.parking.gate.GateRecord;
import com.parking.payment.PaymentSession;
import com.parking.payment.PaymentTerminal;
import com.parking.space.ReservationManager;
import com.parking.space.SpaceReservation;
import java.util.Optional;

/**
 * Looks at what is really persisted in A, B and C and undoes the right part of a chain,
 * in reverse order: C (if a session exists), then B, then A. Safe to run more than once.
 */
class ChainCompensator {
    private final GateController gate;
    private final ReservationManager space;
    private final PaymentTerminal payment;
    private final ChainRepository chains;

    ChainCompensator(GateController gate, ReservationManager space, PaymentTerminal payment,
                     ChainRepository chains) {
        this.gate = gate;
        this.space = space;
        this.payment = payment;
        this.chains = chains;
    }

    ChainRecord unwind(ChainRecord chain, String reason) {
        chain.compensating(reason);
        chains.save(chain);

        Optional<SpaceReservation> reservation = space.findByCorrelationId(chain.getGateId());
        if (reservation.isPresent()) {
            SpaceReservation r = reservation.get();
            payment.findByCorrelationId(r.getId()).ifPresent(p -> {
                if (p.getStatus() != PaymentSession.Status.FAILED) {
                    payment.voidSession(p.getId());
                }
            });
            if (r.getStatus() == SpaceReservation.Status.RESERVED) {
                space.releaseSpot(r.getId());
            }
        }
        gate.find(chain.getGateId()).ifPresent(g -> {
            if (g.getStatus() != GateRecord.Status.CANCELLED) {
                gate.cancelEntry(g.getId());
            }
        });

        chain.compensated();
        chains.save(chain);
        return chain;
    }

    /** After a restart: finish the chain if C really committed, otherwise unwind it. */
    ChainRecord recover(ChainRecord chain) {
        if (chain.getStatus() == ChainRecord.Status.COMPENSATING) {
            return unwind(chain, "");
        }
        Optional<SpaceReservation> reservation = space.findByCorrelationId(chain.getGateId());
        Optional<PaymentSession> session = reservation.flatMap(r -> payment.findByCorrelationId(r.getId()));
        boolean committed = session.isPresent()
                && session.get().getStatus() == PaymentSession.Status.ACTIVE
                && reservation.get().getStatus() == SpaceReservation.Status.RESERVED;
        if (committed) {
            chain.complete(reservation.get().getId(), session.get().getId());
            chains.save(chain);
            return chain;
        }
        return unwind(chain, "Recovered after restart: chain was not finished");
    }
}
