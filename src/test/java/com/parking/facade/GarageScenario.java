package com.parking.facade;

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
import java.util.Optional;

/**
 * Small domain-specific testing language. Reads like:
 * emptyGarage(dir, 2).paymentTerminalIsOffline().vehicleParks("123ABC02", 2)
 * Every "persisted..." helper reads from disk through brand-new objects.
 */
final class GarageScenario {
    private final Path dir;
    private final int capacity;
    private ParkingGarage garage;

    private GarageScenario(Path dir, int capacity) {
        this.dir = dir;
        this.capacity = capacity;
        this.garage = new ParkingGarage(dir, capacity);
    }

    static GarageScenario emptyGarage(Path dir, int capacity) {
        return new GarageScenario(dir, capacity);
    }

    GarageScenario sensorGridIsOffline() {
        garage.adminFacade().setSensorGridOnline(false);
        return this;
    }

    GarageScenario paymentTerminalIsOffline() {
        garage.adminFacade().setPaymentTerminalOnline(false);
        return this;
    }

    GarageScenario paymentTerminalIsBackOnline() {
        garage.adminFacade().setPaymentTerminalOnline(true);
        return this;
    }

    GarageScenario garageRestarts() {
        garage = new ParkingGarage(dir, capacity);
        return this;
    }

    ParkingAdminFacade admin() {
        return garage.adminFacade();
    }

    ChainRecord vehicleParks(String plate, int hours) {
        return garage.facade().parkVehicle(plate, hours);
    }

    GateRecord.Status persistedGateOf(ChainRecord chain) {
        return new GateController(new GateRepository(dir)).find(chain.getGateId()).orElseThrow().getStatus();
    }

    Optional<SpaceReservation> persistedReservationOf(ChainRecord chain) {
        return new ReservationManager(new SpaceRepository(dir), new SpaceAllocator(), capacity)
                .findByCorrelationId(chain.getGateId());
    }

    Optional<PaymentSession> persistedPaymentOf(ChainRecord chain) {
        return persistedReservationOf(chain).flatMap(r ->
                new PaymentTerminal(new PaymentRepository(dir), new OverstayPolicy())
                        .findByCorrelationId(r.getId()));
    }

    ChainRecord.Status persistedChainStatus(ChainRecord chain) {
        return new ChainRepository(dir).findById(chain.getId()).orElseThrow().getStatus();
    }
}
