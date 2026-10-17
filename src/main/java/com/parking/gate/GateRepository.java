package com.parking.gate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence gateway of subsystem A: stores gate records in gate.csv. */
public class GateRepository {
    private final Path dir;
    private final Path file;

    public GateRepository(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("gate.csv");
    }

    public synchronized void save(GateRecord record) {
        Map<String, GateRecord> all = load();
        all.put(record.getId(), record);
        write(all.values());
    }

    public synchronized Optional<GateRecord> findById(String id) {
        return Optional.ofNullable(load().get(id));
    }

    public synchronized List<GateRecord> findAll() {
        return new ArrayList<>(load().values());
    }

    private Map<String, GateRecord> load() {
        Map<String, GateRecord> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                result.put(p[0], new GateRecord(p[0], p[1], GateRecord.Status.valueOf(p[2])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private void write(Collection<GateRecord> records) {
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            for (GateRecord r : records) {
                lines.add(r.getId() + "," + r.getPlateNumber() + "," + r.getStatus());
            }
            Path tmp = dir.resolve("gate.csv.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
