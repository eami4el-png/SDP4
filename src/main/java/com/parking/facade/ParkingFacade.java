package com.parking.facade;

import com.parking.gate.GateController;
import com.parking.gate.GateRecord;
import com.parking.gate.InvalidPlateException;
import com.parking.payment.PaymentSession;
import com.parking.payment.PaymentTerminal;
import com.parking.space.ReservationManager;
import com.parking.space.SpaceReservation;

/** Client facade: one operation that drives A (gate) -> B (space) -> C (payment). */
public class ParkingFacade {
    private final GateController gate;
    private final ReservationManager space;
    private final PaymentTerminal payment;
    private final ChainRepository chains;
    private final ChainCompensator compensator;

    ParkingFacade(GateController gate, ReservationManager space, PaymentTerminal payment,
                  ChainRepository chains) {
        this.gate = gate;
        this.space = space;
        this.payment = payment;
        this.chains = chains;
        this.compensator = new ChainCompensator(gate, space, payment, chains);
    }

    /**
     * Parks a vehicle. Returns the chain: COMPLETED on success, COMPENSATED if B or C failed
     * (everything already committed has been undone). Throws ParkingRejectedException if the
     * request is rejected at step A, before anything was committed.
     */
    public ChainRecord parkVehicle(String plate, int hours) {
        GateRecord entry;
        try {
            entry = gate.registerEntry(plate);
        } catch (InvalidPlateException e) {
            throw new ParkingRejectedException(e.getMessage(), e);
        }

        ChainRecord chain = ChainRecord.start(entry.getPlateNumber(), entry.getId(), hours);
        chains.save(chain);

        try {
            SpaceReservation reservation = space.reserveSpot(entry.getId());
            chain.awaitPayment(reservation.getId());
            chains.save(chain);

            PaymentSession session = payment.authorize(reservation.getId(), hours);
            chain.complete(reservation.getId(), session.getId());
            chains.save(chain);
            return chain;
        } catch (RuntimeException e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return compensator.unwind(chain, reason);
        }
    }
}
