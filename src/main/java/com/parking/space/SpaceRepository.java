package com.parking.space;

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

/** Persistence gateway of subsystem B: stores reservations in space.csv. */
public class SpaceRepository {
    private final Path dir;
    private final Path file;

    public SpaceRepository(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("space.csv");
    }

    public synchronized void save(SpaceReservation reservation) {
        Map<String, SpaceReservation> all = load();
        all.put(reservation.getId(), reservation);
        write(all.values());
    }

    public synchronized Optional<SpaceReservation> findById(String id) {
        return Optional.ofNullable(load().get(id));
    }

    public synchronized Optional<SpaceReservation> findByCorrelationId(String correlationId) {
        return load().values().stream()
                .filter(r -> r.getCorrelationId().equals(correlationId))
                .findFirst();
    }

    public synchronized List<SpaceReservation> findAll() {
        return new ArrayList<>(load().values());
    }

    private Map<String, SpaceReservation> load() {
        Map<String, SpaceReservation> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                result.put(p[0], new SpaceReservation(p[0], p[1], Integer.parseInt(p[2]),
                        SpaceReservation.Status.valueOf(p[3])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private void write(Collection<SpaceReservation> reservations) {
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            for (SpaceReservation r : reservations) {
                lines.add(r.getId() + "," + r.getCorrelationId() + "," + r.getSpotNumber() + "," + r.getStatus());
            }
            Path tmp = dir.resolve("space.csv.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
