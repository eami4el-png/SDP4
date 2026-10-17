package com.parking.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReservationManagerTest {

    @TempDir
    Path dir;

    private ReservationManager managerWithCapacity(int capacity) {
        return new ReservationManager(new SpaceRepository(dir), new SpaceAllocator(), capacity);
    }

    @Test
    @DisplayName("Spots are given out lowest-first and survive a restart")
    void reservesLowestFreeSpotAndPersists() {
        ReservationManager manager = managerWithCapacity(2);

        SpaceReservation first = manager.reserveSpot("car-1");
        SpaceReservation second = manager.reserveSpot("car-2");

        assertEquals(1, first.getSpotNumber());
        assertEquals(2, second.getSpotNumber());
        SpaceReservation reloaded = managerWithCapacity(2).findByCorrelationId("car-2").orElseThrow();
        assertEquals(SpaceReservation.Status.RESERVED, reloaded.getStatus());
        assertEquals(2, managerWithCapacity(2).all().size());
    }

    @Test
    void fullGarageRejectsReservationAndSavesNothing() {
        ReservationManager manager = managerWithCapacity(1);
        manager.reserveSpot("car-1");

        assertThrows(SpaceUnavailableException.class, () -> manager.reserveSpot("car-2"));
        assertEquals(1, manager.all().size());
    }

    @Test
    void releasedSpotCanBeUsedAgain() {
        ReservationManager manager = managerWithCapacity(1);
        SpaceReservation first = manager.reserveSpot("car-1");
        manager.releaseSpot(first.getId());

        SpaceReservation second = manager.reserveSpot("car-2");

        assertEquals(1, second.getSpotNumber());
        assertEquals(SpaceReservation.Status.RELEASED, manager.find(first.getId()).orElseThrow().getStatus());
    }

    @Test
    void offlineSensorGridRejectsReservation() {
        ReservationManager manager = managerWithCapacity(3);
        manager.setSensorGridOnline(false);

        assertThrows(SpaceUnavailableException.class, () -> manager.reserveSpot("car-1"));
        assertTrue(manager.all().isEmpty());

        manager.setSensorGridOnline(true);
        assertEquals(1, manager.reserveSpot("car-1").getSpotNumber());
    }

    @Test
    void illegalTransitionsAreRefused() {
        ReservationManager manager = managerWithCapacity(1);
        SpaceReservation reservation = manager.reserveSpot("car-1");
        manager.releaseSpot(reservation.getId());

        assertThrows(IllegalStateException.class, () -> manager.releaseSpot(reservation.getId()));
        assertThrows(IllegalStateException.class, () -> reservation.reserve(1));
        assertThrows(NoSuchElementException.class, () -> manager.releaseSpot("missing"));
    }

    @Test
    void allocatorSkipsOccupiedSpots() {
        assertEquals(2, new SpaceAllocator().nextFreeSpot(3, List.of(1, 3)));
        assertThrows(SpaceUnavailableException.class, () -> new SpaceAllocator().nextFreeSpot(2, List.of(1, 2)));
    }
}
