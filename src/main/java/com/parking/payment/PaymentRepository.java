package com.parking.payment;

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

/** Persistence gateway of subsystem C: stores payment sessions in payment.csv. */
public class PaymentRepository {
    private final Path dir;
    private final Path file;

    public PaymentRepository(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("payment.csv");
    }

    public synchronized void save(PaymentSession session) {
        Map<String, PaymentSession> all = load();
        all.put(session.getId(), session);
        write(all.values());
    }

    public synchronized Optional<PaymentSession> findById(String id) {
        return Optional.ofNullable(load().get(id));
    }

    public synchronized Optional<PaymentSession> findByCorrelationId(String correlationId) {
        return load().values().stream()
                .filter(s -> s.getCorrelationId().equals(correlationId))
                .findFirst();
    }

    public synchronized List<PaymentSession> findAll() {
        return new ArrayList<>(load().values());
    }

    private Map<String, PaymentSession> load() {
        Map<String, PaymentSession> result = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return result;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                result.put(p[0], new PaymentSession(p[0], p[1], Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]), PaymentSession.Status.valueOf(p[4])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private void write(Collection<PaymentSession> sessions) {
        try {
            Files.createDirectories(dir);
            List<String> lines = new ArrayList<>();
            for (PaymentSession s : sessions) {
                lines.add(s.getId() + "," + s.getCorrelationId() + "," + s.getHours() + ","
                        + s.getFee() + "," + s.getStatus());
            }
            Path tmp = dir.resolve("payment.csv.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
