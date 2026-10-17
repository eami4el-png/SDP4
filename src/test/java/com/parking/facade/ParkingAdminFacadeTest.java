package com.parking.facade;

import static com.parking.facade.GarageScenario.emptyGarage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parking.gate.GateController;
import com.parking.gate.GateRecord;
import com.parking.gate.GateRepository;
import com.parking.payment.OverstayPolicy;
import com.parking.payment.PaymentRepository;
import com.parking.payment.PaymentSession;
import com.parking.payment.PaymentTerminal;
import com.parking.space.ReservationManager;
import com.parking.space.SpaceAllocator;
import com.parking.space.SpaceRepository;
import com.parking.space.SpaceReservation;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParkingAdminFacadeTest {

    @TempDir
    Path dir;

    /** Simulates an application that died right after step A committed. */
    private ChainRecord crashedAfterGate() {
        GateRecord entry = new GateController(new GateRepository(dir)).registerEntry("111AAA01");
        ChainRecord chain = ChainRecord.start(entry.getPlateNumber(), entry.getId(), 2);
        new ChainRepository(dir).save(chain);
        return chain;
    }

    /** Simulates an application that died right after step B committed. */
    private ChainRecord crashedAfterSpace() {
        ChainRecord chain = crashedAfterGate();
        SpaceReservation reservation = new ReservationManager(new SpaceRepository(dir), new SpaceAllocator(), 5)
                .reserveSpot(chain.getGateId());
        chain.awaitPayment(reservation.getId());
        new ChainRepository(dir).save(chain);
        return chain;
    }

    @Test
    @DisplayName("Restart after A only: recovery cancels the gate record")
    void restartAfterGateUndoesGate() {
        ChainRecord crashed = crashedAfterGate();
        GarageScenario garage = emptyGarage(dir, 5).garageRestarts();

        List<ChainRecord> recovered = garage.admin().recoverUnfinished();

        assertEquals(1, recovered.size());
        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(crashed));
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(crashed));
        assertTrue(garage.persistedReservationOf(crashed).isEmpty());
    }

    @Test
    @DisplayName("Restart after A and B: recovery releases the spot, then cancels the gate record")
    void restartAfterSpaceUndoesSpaceThenGate() {
        ChainRecord crashed = crashedAfterSpace();
        GarageScenario garage = emptyGarage(dir, 5).garageRestarts();

        garage.admin().recoverUnfinished();

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(crashed));
        assertEquals(SpaceReservation.Status.RELEASED, garage.persistedReservationOf(crashed).orElseThrow().getStatus());
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(crashed));
    }

    @Test
    @DisplayName("Restart when C had really committed: recovery completes the chain and undoes nothing")
    void restartAfterPaymentCommittedCompletesChain() {
        ChainRecord crashed = crashedAfterSpace();
        new PaymentTerminal(new PaymentRepository(dir), new OverstayPolicy())
                .authorize(crashed.getReservationId(), 2);
        GarageScenario garage = emptyGarage(dir, 5).garageRestarts();

        garage.admin().recoverUnfinished();

        assertEquals(ChainRecord.Status.COMPLETED, garage.persistedChainStatus(crashed));
        assertEquals(GateRecord.Status.ENTERED, garage.persistedGateOf(crashed));
        assertEquals(PaymentSession.Status.ACTIVE, garage.persistedPaymentOf(crashed).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Restart in the middle of a compensation: recovery finishes it")
    void restartDuringCompensationFinishesIt() {
        ChainRecord crashed = crashedAfterSpace();
        crashed.compensating("terminal offline");
        new ChainRepository(dir).save(crashed);
        GarageScenario garage = emptyGarage(dir, 5).garageRestarts();

        garage.admin().recoverUnfinished();

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(crashed));
        assertEquals(SpaceReservation.Status.RELEASED, garage.persistedReservationOf(crashed).orElseThrow().getStatus());
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(crashed));
    }

    @Test
    void finishedChainsAreLeftAlone() {
        GarageScenario garage = emptyGarage(dir, 2);
        ChainRecord done = garage.vehicleParks("111AAA01", 1);
        garage.garageRestarts();

        assertTrue(garage.admin().recoverUnfinished().isEmpty());
        assertEquals(ChainRecord.Status.COMPLETED, garage.persistedChainStatus(done));
    }

    @Test
    @DisplayName("Manual compensation of a completed chain undoes C, B and A")
    void manualCompensationUndoesWholeChain() {
        GarageScenario garage = emptyGarage(dir, 2);
        ChainRecord chain = garage.vehicleParks("111AAA01", 1);

        garage.admin().compensate(chain.getId());

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(chain));
        assertEquals(PaymentSession.Status.FAILED, garage.persistedPaymentOf(chain).orElseThrow().getStatus());
        assertEquals(SpaceReservation.Status.RELEASED, garage.persistedReservationOf(chain).orElseThrow().getStatus());
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(chain));
    }

    @Test
    void manualCompensationRefusesUnknownAndAlreadyCompensatedChains() {
        GarageScenario garage = emptyGarage(dir, 2);
        ChainRecord chain = garage.vehicleParks("111AAA01", 1);
        garage.admin().compensate(chain.getId());

        assertThrows(IllegalStateException.class, () -> garage.admin().compensate(chain.getId()));
        assertThrows(NoSuchElementException.class, () -> garage.admin().compensate("missing"));
    }

    @Test
    void describeStateListsPersistedRecordsOfAllSubsystems() {
        GarageScenario garage = emptyGarage(dir, 2).paymentTerminalIsOffline();
        garage.vehicleParks("111AAA01", 1);

        String report = garage.admin().describeState();

        assertTrue(report.contains("plate=111AAA01"));
        assertTrue(report.contains("status=COMPENSATED"));
        assertTrue(report.contains("status=CANCELLED"));
        assertTrue(report.contains("status=RELEASED"));
        assertTrue(report.contains("status=FAILED"));
    }

    @Test
    void chainRecordRejectsIllegalTransitions() {
        ChainRecord chain = ChainRecord.start("111AAA01", "gate-1", 1);
        chain.awaitPayment("res-1");

        assertThrows(IllegalStateException.class, () -> chain.awaitPayment("res-1"));
        assertThrows(IllegalStateException.class, chain::compensated);

        chain.compensating("boom");
        chain.compensated();
        assertThrows(IllegalStateException.class, () -> chain.complete("res-1", "pay-1"));
        assertThrows(IllegalStateException.class, () -> chain.compensating("again"));
    }
}
