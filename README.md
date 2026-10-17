# Smart Parking Garage: Facade over a three-stage chain

Assignment 4, Facade pattern. Author: <your name>.

A vehicle enters a parking garage. One client operation, `parkVehicle(plate, hours)`, drives three independent subsystems in order:

| Stage | Subsystem | Classes | What it does |
|---|---|---|---|
| A | Gate (Gate Controller, License Plate Reader) | `GateController`, `GateRecord`, `PlateReader`, `GateRepository`, `InvalidPlateException` | Validates the plate and opens the gate |
| B | Space (Space Sensor Grid, Reservation Manager) | `ReservationManager`, `SpaceReservation`, `SpaceAllocator`, `SpaceRepository`, `SpaceUnavailableException` | Reserves a free spot |
| C | Payment (Payment Terminal, Overstay Alert) | `PaymentTerminal`, `PaymentSession`, `OverstayPolicy`, `PaymentRepository`, `PaymentDeclinedException` | Authorises payment and enforces the stay limit |

The subsystems do not import or call each other. They only meet inside the facade package.

## Facades

- `ParkingFacade` is the client workflow: A -> B -> C, with compensation when B or C fails.
- `ParkingAdminFacade` is the narrow admin facade: inspect persisted state, recover unfinished chains after a restart, compensate a chain manually, switch the sensor grid or payment terminal on and off.
- `ParkingGarage` wires the subsystems and both facades on top of one data directory.

## How to run

Requires Java 17+ and Maven (IntelliJ's bundled Maven also works).

```
mvn compile exec:java
```

Menu of the demo application:

1. Park a vehicle (full chain A -> B -> C).
2. Park a vehicle while the space sensor grid fails (B fails, A is undone).
3. Park a vehicle while the payment terminal fails (C fails, B then A are undone).
4. Inspect persisted state of all subsystems and chains.
5. Exit.

A stay longer than 24 hours is also declined at stage C, which triggers the same two-step rollback with real input.

## Durable state

Each subsystem persists its own state through its own repository into a CSV file in the `data/` folder (`gate.csv`, `space.csv`, `payment.csv`). The facade persists the progress of every chain in `chain.csv` with the statuses `PENDING_B`, `PENDING_C`, `COMPLETED`, `COMPENSATING`, `COMPENSATED`.
Files are written to a temporary file and then moved over the old one.

On startup the application calls `recoverUnfinished()`. It reads the unfinished chains from disk and checks what is really stored in A, B and C. If payment is actually committed, the chain is completed; otherwise the right part of the chain is undone.

### Reset to a clean state

Stop the application and delete the `data/` folder. It is recreated on the next run.

## Compensation

| Scenario | Persisted result |
|---|---|
| B fails after A committed | Gate record `CANCELLED`, no reservation, no payment, chain `COMPENSATED` |
| C fails after A and B committed | Payment `FAILED`, reservation `RELEASED`, gate record `CANCELLED` (in this order), chain `COMPENSATED` |
| Invalid plate (stage A, nothing committed yet) | `ParkingRejectedException`, nothing is saved |

Compensation reads the persisted state of the subsystems and is safe to run more than once, so a crash during compensation can also be finished after a restart.

Each entity enforces its own legal status transitions (`GateRecord`: NEW, ENTERED, CANCELLED; `SpaceReservation`: NEW, RESERVED, RELEASED; `PaymentSession`: NEW, ACTIVE, FAILED).

## Tests and coverage

```
mvn test
```

34 unit tests, no test depends on another: each one uses its own temporary data directory. Tests are written as sentences of the domain with the helper class `GarageScenario`, for example `emptyGarage(dir, 2).paymentTerminalIsOffline().vehicleParks("123ABC02", 2)`. Its "persisted..." helpers read the state from disk through new objects.

Covered: the happy path, both compensation depths (B fails; C fails), an overstay, a full garage, an invalid plate, recovery after a restart at every stage of the chain, recovery in the middle of a compensation, manual compensation, and illegal status transitions.

Coverage is measured with JaCoCo. The report is created at `target/site/jacoco/index.html`.

| Package | Instruction coverage | Branch coverage |
|---|---|---|
| `com.parking.facade` | 97% | 84% |
| `com.parking.gate` | 95% | 86% |
| `com.parking.space` | 96% | 95% |
| `com.parking.payment` | 96% | 94% |

The class `Main` (the console menu) is not covered by unit tests and is not part of the subsystems or facades.

## Principle of Least Knowledge

Before, without the facade, the client has to know all three subsystems and their entities, and chain their ids by hand:

```java
GateController gate = new GateController(new GateRepository(dir));
ReservationManager space = new ReservationManager(new SpaceRepository(dir), new SpaceAllocator(), 5);
PaymentTerminal terminal = new PaymentTerminal(new PaymentRepository(dir), new OverstayPolicy());

GateRecord entry = gate.registerEntry(plate);
SpaceReservation reservation = space.reserveSpot(entry.getId());
PaymentSession session = terminal.authorize(reservation.getId(), hours);
// and on failure the client must undo it itself, in the right order:
// terminal.voidSession(...); space.releaseSpot(...); gate.cancelEntry(...);
```

The client talks to three subsystems and uses three entity classes. It must also know which step failed and what to undo.

After, with the facade:

```java
ParkingGarage garage = new ParkingGarage(Path.of("data"), 5);
ChainRecord chain = garage.facade().parkVehicle(plate, hours);
```

The client only talks to `ParkingGarage`, its facade and the returned `ChainRecord`. It does not know the subsystems exist, and it never has to compensate anything: that is the facade's job.

## Facade vs Adapter

An Adapter changes the interface of one existing class so that a client who expects a different interface can use it. Its intent is compatibility. Here nothing is incompatible: the gate, the space manager and the payment terminal each have a clean interface of their own, and the client does not expect any of them in another shape.

Our problem is different. The client wants one logical operation, "park this vehicle", but it is made of three separate steps in three unrelated subsystems that must run in a fixed order, pass results from one to the next, and be undone in reverse order when a later step fails. The intent is to give the client one simpler, higher-level interface over a whole group of subsystems and to hide their coordination, including compensation. That is the intent of a Facade. An Adapter would wrap a single class and could not own the sequencing, the persisted progress or the rollback logic.
