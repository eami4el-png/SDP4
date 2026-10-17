package com.parking.facade;

import com.parking.gate.GateController;
import com.parking.gate.GateRecord;
import com.parking.payment.PaymentSession;
import com.parking.payment.PaymentTerminal;
import com.parking.space.ReservationManager;
import com.parking.space.SpaceReservation;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/** Narrow facade for inspection, recovery after restart and manual compensation. */
public class ParkingAdminFacade {
    private final GateController gate;
    private final ReservationManager space;
    private final PaymentTerminal payment;
    private final ChainRepository chains;
    private final ChainCompensator compensator;

    ParkingAdminFacade(GateController gate, ReservationManager space, PaymentTerminal payment,
                       ChainRepository chains) {
        this.gate = gate;
        this.space = space;
        this.payment = payment;
        this.chains = chains;
        this.compensator = new ChainCompensator(gate, space, payment, chains);
    }

    public void setSensorGridOnline(boolean online) {
        space.setSensorGridOnline(online);
    }

    public void setPaymentTerminalOnline(boolean online) {
        payment.setOnline(online);
    }

    /** Looks at every chain on disk that never finished and completes or undoes it. */
    public List<ChainRecord> recoverUnfinished() {
        List<ChainRecord> recovered = new ArrayList<>();
        for (ChainRecord chain : chains.findAll()) {
            ChainRecord.Status s = chain.getStatus();
            if (s == ChainRecord.Status.PENDING_B || s == ChainRecord.Status.PENDING_C
                    || s == ChainRecord.Status.COMPENSATING) {
                recovered.add(compensator.recover(chain));
            }
        }
        return recovered;
    }

    /** Manually undoes a chain (also a completed one). */
    public ChainRecord compensate(String chainId) {
        ChainRecord chain = chains.findById(chainId)
                .orElseThrow(() -> new NoSuchElementException("No chain " + chainId));
        if (chain.getStatus() == ChainRecord.Status.COMPENSATED) {
            throw new IllegalStateException("Chain " + chainId + " is already compensated");
        }
        return compensator.unwind(chain, "Manual compensation");
    }

    public String describeState() {
        StringBuilder out = new StringBuilder("=== PERSISTED STATE ===\n");
        out.append("-- Chains --\n");
        for (ChainRecord c : chains.findAll()) {
            out.append(String.format("  %s plate=%s hours=%d status=%s%s%n", shortId(c.getId()),
                    c.getPlateNumber(), c.getHours(), c.getStatus(),
                    c.getFailureReason().isEmpty() ? "" : " reason=\"" + c.getFailureReason() + "\""));
        }
        out.append("-- A: Gate records --\n");
        for (GateRecord g : gate.all()) {
            out.append(String.format("  %s plate=%s status=%s%n", shortId(g.getId()),
                    g.getPlateNumber(), g.getStatus()));
        }
        out.append("-- B: Space reservations --\n");
        for (SpaceReservation r : space.all()) {
            out.append(String.format("  %s spot=%d status=%s%n", shortId(r.getId()),
                    r.getSpotNumber(), r.getStatus()));
        }
        out.append("-- C: Payment sessions --\n");
        for (PaymentSession p : payment.all()) {
            out.append(String.format("  %s fee=%d status=%s%n", shortId(p.getId()),
                    p.getFee(), p.getStatus()));
        }
        return out.toString();
    }

    private static String shortId(String id) {
        return id.substring(0, 8);
    }
}
