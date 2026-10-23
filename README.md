# Smart Parking Lot System

Low-level design and implementation of a smart parking lot backend in Java 21+, with no runtime dependencies.

## Run

```bash
mvn test                      # 23 unit + concurrency tests
mvn compile exec:java         # walkthrough demo (com.parkinglot.demo.Main)
```

## Package layout

| Package       | Responsibility |
|---------------|----------------|
| `model`       | Domain: `Vehicle`, `VehicleType`, `SpotSize`, `ParkingSpot`, `ParkingFloor`, `ParkingLot` (+Builder), `ParkingTicket`, `Receipt`, `FloorAvailability` |
| `allocation`  | `SpotAllocationStrategy` → `BestFitAllocationStrategy`, `NearestFloorAllocationStrategy` |
| `pricing`     | `FeeCalculator` → `HourlyFeeCalculator`, `DailyCapFeeCalculator` (decorator) |
| `repository`  | `TicketRepository` port + `InMemoryTicketRepository` adapter |
| `event`       | `ParkingEventPublisher` (observer), `ParkingEvent` (sealed), `AvailabilityDisplayBoard` |
| `service`     | `ParkingService` interface, `DefaultParkingService` (check-in / check-out orchestration) |
| `exception`   | `ParkingException` hierarchy with stable `ErrorCode`s |
| `resources/schema.sql` | Relational data model (PostgreSQL) |

## Design patterns
- **Strategy**: spot allocation and fee calculation are swappable.
- **Decorator**: `DailyCapFeeCalculator` adds a pricing rule without changing `HourlyFeeCalculator`.
- **Observer**: entry/exit events push real-time availability to displays and apps.
- **Builder**: `ParkingLot.builder()` validates the whole layout before it can be used.
- **Repository**: persistence sits behind an interface.
- **Facade**: `ParkingService` is the single entry point for gates and controllers.

## SOLID
- **S**: each class has one job (allocation, pricing, storage, notification and orchestration are separate).
- **O**: you add a new pricing rule or allocation policy by writing a new class, not by editing existing ones.
- **L**: any `FeeCalculator` or `SpotAllocationStrategy` can be swapped in without changing callers.
- **I**: the interfaces are small (one or a few methods).
- **D**: `DefaultParkingService` depends only on abstractions, plus an injected `Clock` and ID generator so tests are deterministic.

## Spot allocation algorithm
Each floor keeps one `ConcurrentSkipListSet` of free spots per size, ordered by spot number.
`BestFitAllocationStrategy` tries the smallest size that fits first, on every floor, before moving
up a size, so larger spots stay free for larger vehicles. `pollFirst()` atomically removes and returns
the free spot, so allocation is **O(S·F·log N)** and needs no locks.
Releasing a spot puts it back in the set, which keeps availability counts current in real time.

## Fee calculation
- Stays within the grace period (e.g. 10 min) are free.
- Otherwise every started hour is billed at the vehicle type's hourly rate (minimum 1 hour).
- Optional daily cap: `fullDays × cap + min(remainderFee, cap)`.
- Uses `BigDecimal` with scale 2. Rate cards are validated when the calculator is built, so a missing rate fails at startup, not at the exit gate.

## Concurrency
| Hazard | Guard |
|--------|-------|
| Two vehicles get the same spot | Lock-free `pollFirst()` on the free list, plus a CAS on `ParkingSpot.occupied` that catches invariant violations |
| Same vehicle checked in twice (two gates) | `ConcurrentHashMap.compute` on the plate, which is atomic per key |
| Same ticket checked out twice | `activeSessions.remove(plate, ticket)`: only one caller wins |
| Allocation succeeds but saving fails | The spot is released again, so it never leaks |
| Pricing fails | The fee is computed before any state changes, so the session stays intact and checkout can be retried |
| Display/listener crashes | The publisher isolates and logs the failure, so entry and exit are unaffected |

Across multiple app instances, the database enforces the same rules: partial unique indexes on
active tickets per vehicle and per spot, plus `SELECT … FOR UPDATE SKIP LOCKED` for allocation (see `schema.sql`).

## Error handling
All domain errors extend `ParkingException` and carry an `ErrorCode`
(`NO_SPOT_AVAILABLE`, `VEHICLE_ALREADY_PARKED`, `TICKET_NOT_FOUND`, `TICKET_ALREADY_CLOSED`,
`VEHICLE_NOT_PARKED`, `INVALID_VEHICLE`, `INVALID_CONFIGURATION`, `PRICING_ERROR`), which an API layer can map to HTTP status codes.
Input is validated where it enters the system (license-plate format, layout, rate cards).
`IllegalStateException` is reserved for internal invariant violations.
