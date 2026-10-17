package com.parking.gate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Subsystem A entry point: the gate controller. */
public class GateController {
    private final GateRepository repository;
    private final PlateReader plateReader = new PlateReader();

    public GateController(GateRepository repository) {
        this.repository = repository;
    }

    public GateRecord registerEntry(String rawPlate) {
        String plate = plateReader.read(rawPlate);
        GateRecord record = GateRecord.create(plate);
        repository.save(record);
        record.enter();
        repository.save(record);
        return record;
    }

    public void cancelEntry(String recordId) {
        GateRecord record = repository.findById(recordId)
                .orElseThrow(() -> new NoSuchElementException("No gate record " + recordId));
        record.cancel();
        repository.save(record);
    }

    public Optional<GateRecord> find(String recordId) {
        return repository.findById(recordId);
    }

    public List<GateRecord> all() {
        return repository.findAll();
    }
}
