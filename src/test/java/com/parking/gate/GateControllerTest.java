package com.parking.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateControllerTest {

    @TempDir
    Path dir;

    private GateController newGate() {
        return new GateController(new GateRepository(dir));
    }

    @Test
    @DisplayName("Entry is normalised, saved as ENTERED and readable after a restart")
    void registersEntryAndPersistsIt() {
        GateRecord record = newGate().registerEntry("  123abc02 ");

        GateRecord reloaded = newGate().find(record.getId()).orElseThrow();
        assertEquals("123ABC02", reloaded.getPlateNumber());
        assertEquals(GateRecord.Status.ENTERED, reloaded.getStatus());
    }

    @Test
    @DisplayName("Invalid or empty plates are rejected and nothing is saved")
    void rejectsInvalidPlates() {
        GateController gate = newGate();

        assertThrows(InvalidPlateException.class, () -> gate.registerEntry("!!"));
        assertThrows(InvalidPlateException.class, () -> gate.registerEntry("   "));
        assertThrows(InvalidPlateException.class, () -> gate.registerEntry(null));
        assertTrue(newGate().all().isEmpty());
    }

    @Test
    void cancelEntryMarksRecordCancelled() {
        GateController gate = newGate();
        GateRecord record = gate.registerEntry("ABC123");

        gate.cancelEntry(record.getId());

        assertEquals(GateRecord.Status.CANCELLED, newGate().find(record.getId()).orElseThrow().getStatus());
    }

    @Test
    void cancelledEntryCannotBeCancelledAgain() {
        GateController gate = newGate();
        GateRecord record = gate.registerEntry("ABC123");
        gate.cancelEntry(record.getId());

        assertThrows(IllegalStateException.class, () -> gate.cancelEntry(record.getId()));
    }

    @Test
    void cancelledRecordCannotEnter() {
        GateRecord record = GateRecord.create("ABC123");
        record.cancel();

        assertThrows(IllegalStateException.class, record::enter);
    }

    @Test
    void cancellingUnknownRecordFails() {
        assertThrows(NoSuchElementException.class, () -> newGate().cancelEntry("missing"));
    }
}
