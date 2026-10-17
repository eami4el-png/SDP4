package com.parking.facade;

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

/** Stores the progress of every chain in chain.csv, so it survives a restart. */
public class ChainRepository {
    private final Path dir;
    private final Path file;

    public ChainRepository(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("chain.csv");
    }

    public synchronized void save(ChainRecord chain) {
        Map<String, ChainRecord> all = load();
        all.put(chain.getId(), chain);
        write(all.values());
    }

    public synchronized Optional<ChainRecord> findById(String id) {
        return Optional.ofNullable(load().get(id));
    }

    public synchronized List<ChainRecord> findAll() {
        return new ArrayList<>(load().values());
    }

    private Map<String, ChainRecord> load() {
        Map<String, ChainRecord> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", 8);
                result.put(p[0], new ChainRecord(p[0], p[1], p[2], Integer.parseInt(p[3]), p[4], p[5],
                        ChainRecord.Status.valueOf(p[6]), p.length > 7 ? p[7] : ""));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private void write(Collection<ChainRecord> chains) {
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            for (ChainRecord c : chains) {
                lines.add(String.join(",", c.getId(), c.getPlateNumber(), c.getGateId(),
                        String.valueOf(c.getHours()), c.getReservationId(), c.getPaymentId(),
                        c.getStatus().name(), c.getFailureReason()));
            }
            Path tmp = dir.resolve("chain.csv.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
