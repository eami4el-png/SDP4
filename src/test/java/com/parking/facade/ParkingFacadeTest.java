package com.parking.facade;

import static com.parking.facade.GarageScenario.emptyGarage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.parking.gate.GateController;
import com.parking.gate.GateRecord;
import com.parking.gate.GateRepository;
import com.parking.payment.PaymentSession;
import com.parking.space.SpaceReservation;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParkingFacadeTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Happy path: A, B and C all commit and the chain is COMPLETED")
    void happyPath() {
        GarageScenario garage = emptyGarage(dir, 2);

        ChainRecord chain = garage.vehicleParks("123ABC02", 2);

        assertEquals(ChainRecord.Status.COMPLETED, garage.persistedChainStatus(chain));
        assertEquals(GateRecord.Status.ENTERED, garage.persistedGateOf(chain));
        assertEquals(SpaceReservation.Status.RESERVED, garage.persistedReservationOf(chain).orElseThrow().getStatus());
        assertEquals(PaymentSession.Status.ACTIVE, garage.persistedPaymentOf(chain).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Depth 1: B fails after A committed -> only A is compensated")
    void sensorGridFailureCompensatesGateOnly() {
        GarageScenario garage = emptyGarage(dir, 2).sensorGridIsOffline();

        ChainRecord chain = garage.vehicleParks("123ABC02", 2);

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(chain));
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(chain));
        assertTrue(garage.persistedReservationOf(chain).isEmpty());
        assertTrue(garage.persistedPaymentOf(chain).isEmpty());
        assertTrue(chain.getFailureReason().contains("sensor grid"));
    }

    @Test
    @DisplayName("Depth 2: C fails after A and B committed -> B then A are compensated")
    void paymentFailureCompensatesSpaceThenGate() {
        GarageScenario garage = emptyGarage(dir, 2).paymentTerminalIsOffline();

        ChainRecord chain = garage.vehicleParks("123ABC02", 2);

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(chain));
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(chain));
        assertEquals(SpaceReservation.Status.RELEASED, garage.persistedReservationOf(chain).orElseThrow().getStatus());
        assertEquals(PaymentSession.Status.FAILED, garage.persistedPaymentOf(chain).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Depth 2 again, caused by a business rule: stay longer than 24 h is declined at C")
    void overstayRequestIsCompensatedLikeAPaymentFailure() {
        GarageScenario garage = emptyGarage(dir, 2);

        ChainRecord chain = garage.vehicleParks("123ABC02", 30);

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(chain));
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(chain));
        assertEquals(SpaceReservation.Status.RELEASED, garage.persistedReservationOf(chain).orElseThrow().getStatus());
        assertTrue(chain.getFailureReason().contains("overstay"));
    }

    @Test
    @DisplayName("Depth 1 again, caused by a real condition: garage is full")
    void fullGarageCompensatesGateAndKeepsEarlierVehicle() {
        GarageScenario garage = emptyGarage(dir, 1);
        ChainRecord first = garage.vehicleParks("111AAA01", 1);

        ChainRecord second = garage.vehicleParks("222BBB02", 1);

        assertEquals(ChainRecord.Status.COMPENSATED, garage.persistedChainStatus(second));
        assertEquals(GateRecord.Status.CANCELLED, garage.persistedGateOf(second));
        assertEquals(ChainRecord.Status.COMPLETED, garage.persistedChainStatus(first));
        assertEquals(GateRecord.Status.ENTERED, garage.persistedGateOf(first));
    }

    @Test
    void invalidPlateIsRejectedBeforeAnythingIsCommitted() {
        GarageScenario garage = emptyGarage(dir, 2);

        assertThrows(ParkingRejectedException.class, () -> garage.vehicleParks("!", 2));

        assertTrue(new ChainRepository(dir).findAll().isEmpty());
        assertTrue(new GateController(new GateRepository(dir)).all().isEmpty());
    }

    @Test
    @DisplayName("A spot freed by a compensation can be used by the next vehicle")
    void spotFreedByCompensationIsReused() {
        GarageScenario garage = emptyGarage(dir, 1).paymentTerminalIsOffline();
        garage.vehicleParks("111AAA01", 1);
        garage.paymentTerminalIsBackOnline();

        ChainRecord next = garage.vehicleParks("222BBB02", 1);

        assertEquals(ChainRecord.Status.COMPLETED, garage.persistedChainStatus(next));
    }
}
