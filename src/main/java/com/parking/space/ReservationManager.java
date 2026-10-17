package com.parking.space;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/** Subsystem B entry point: reservation manager backed by the space sensor grid. */
public class ReservationManager {
    private final SpaceRepository repository;
    private final SpaceAllocator allocator;
    private final int capacity;
    private boolean sensorGridOnline = true;

    public ReservationManager(SpaceRepository repository, SpaceAllocator allocator, int capacity) {
        this.repository = repository;
        this.allocator = allocator;
        this.capacity = capacity;
    }

    public void setSensorGridOnline(boolean online) {
        this.sensorGridOnline = online;
    }

    public SpaceReservation reserveSpot(String correlationId) {
        if (!sensorGridOnline) {
            throw new SpaceUnavailableException("Space sensor grid is offline");
        }
        List<Integer> occupied = repository.findAll().stream()
                .filter(r -> r.getStatus() == SpaceReservation.Status.RESERVED)
                .map(SpaceReservation::getSpotNumber)
                .collect(Collectors.toList());
        int spot = allocator.nextFreeSpot(capacity, occupied);
        SpaceReservation reservation = SpaceReservation.create(correlationId);
        reservation.reserve(spot);
        repository.save(reservation);
        return reservation;
    }

    public void releaseSpot(String reservationId) {
        SpaceReservation reservation = repository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("No reservation " + reservationId));
        reservation.release();
        repository.save(reservation);
    }

    public Optional<SpaceReservation> find(String reservationId) {
        return repository.findById(reservationId);
    }

    public Optional<SpaceReservation> findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId);
    }

    public List<SpaceReservation> all() {
        return repository.findAll();
    }
}
