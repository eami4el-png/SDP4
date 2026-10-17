package com.parking.facade;

import com.parking.gate.GateController;
import com.parking.gate.GateRepository;
import com.parking.payment.OverstayPolicy;
import com.parking.payment.PaymentRepository;
import com.parking.payment.PaymentTerminal;
import com.parking.space.ReservationManager;
import com.parking.space.SpaceAllocator;
import com.parking.space.SpaceRepository;
import java.nio.file.Path;

/** Wires the three subsystems and both facades on top of one data directory. */
public class ParkingGarage {
    private final ParkingFacade facade;
    private final ParkingAdminFacade adminFacade;

    public ParkingGarage(Path dataDir, int capacity) {
        GateController gate = new GateController(new GateRepository(dataDir));
        ReservationManager space = new ReservationManager(new SpaceRepository(dataDir), new SpaceAllocator(), capacity);
        PaymentTerminal payment = new PaymentTerminal(new PaymentRepository(dataDir), new OverstayPolicy());
        ChainRepository chains = new ChainRepository(dataDir);
        this.facade = new ParkingFacade(gate, space, payment, chains);
        this.adminFacade = new ParkingAdminFacade(gate, space, payment, chains);
    }

    public ParkingFacade facade() { return facade; }
    public ParkingAdminFacade adminFacade() { return adminFacade; }
}
