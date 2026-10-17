package com.parking.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaymentTerminalTest {

    @TempDir
    Path dir;

    private PaymentTerminal newTerminal() {
        return new PaymentTerminal(new PaymentRepository(dir), new OverstayPolicy());
    }

    @Test
    @DisplayName("Authorised payment is ACTIVE, priced per hour and readable after a restart")
    void authorisesAndPersists() {
        PaymentSession session = newTerminal().authorize("res-1", 3);

        PaymentSession reloaded = newTerminal().find(session.getId()).orElseThrow();
        assertEquals(PaymentSession.Status.ACTIVE, reloaded.getStatus());
        assertEquals(3 * OverstayPolicy.RATE_PER_HOUR, reloaded.getFee());
        assertEquals("res-1", newTerminal().findByCorrelationId("res-1").orElseThrow().getCorrelationId());
    }

    @Test
    void overstayIsDeclinedAndSessionIsMarkedFailed() {
        PaymentTerminal terminal = newTerminal();

        assertThrows(PaymentDeclinedException.class, () -> terminal.authorize("res-1", 25));

        PaymentSession stored = terminal.findByCorrelationId("res-1").orElseThrow();
        assertEquals(PaymentSession.Status.FAILED, stored.getStatus());
    }

    @Test
    void zeroHoursIsDeclined() {
        assertThrows(PaymentDeclinedException.class, () -> newTerminal().authorize("res-1", 0));
    }

    @Test
    void offlineTerminalDeclinesPayment() {
        PaymentTerminal terminal = newTerminal();
        terminal.setOnline(false);

        assertThrows(PaymentDeclinedException.class, () -> terminal.authorize("res-1", 2));
        assertEquals(PaymentSession.Status.FAILED, terminal.findByCorrelationId("res-1").orElseThrow().getStatus());

        terminal.setOnline(true);
        assertEquals(PaymentSession.Status.ACTIVE, terminal.authorize("res-2", 2).getStatus());
        assertEquals(2, terminal.all().size());
    }

    @Test
    void voidingAnActiveSessionFailsIt() {
        PaymentTerminal terminal = newTerminal();
        PaymentSession session = terminal.authorize("res-1", 1);

        terminal.voidSession(session.getId());

        assertEquals(PaymentSession.Status.FAILED, terminal.find(session.getId()).orElseThrow().getStatus());
    }

    @Test
    void illegalTransitionsAreRefused() {
        PaymentTerminal terminal = newTerminal();
        PaymentSession session = terminal.authorize("res-1", 1);
        terminal.voidSession(session.getId());

        assertThrows(IllegalStateException.class, () -> terminal.voidSession(session.getId()));
        assertThrows(IllegalStateException.class, () -> session.activate(100));
        assertThrows(NoSuchElementException.class, () -> terminal.voidSession("missing"));
        assertTrue(terminal.find("missing").isEmpty());
    }
}
