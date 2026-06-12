# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

TKBC-DB (`io.github.vatisteve:tkbc-db`) is a small Java library providing strongly-typed "statistic" DTOs and a JPA stored-procedure executor for reporting/analytics use cases. It is published to Maven Central via OSSRH and is meant to be consumed as a dependency by other Hibernate/JPA applications — there is no standalone runnable application here.

## Build & test commands

- Build: `mvn compile`
- Run all tests: `mvn test`
- Run a single test class: `mvn test -Dtest=StoredProcedureParameterInTest`
- Run a single test method: `mvn test -Dtest=StoredProcedureParameterInTest#testGettersAndTypeReturnCorrectClass`
- Package the jar: `mvn package`

Notes:
- Java source/target is **1.8**, but build/test with **JDK 11+ (JDK 17 recommended)** — Lombok 1.18.30 and the H2 test dependency don't run under very new JDKs (e.g. JDK 25's javac breaks Lombok's annotation processor). Locally: `JAVA_HOME=<jdk17> mvn test`.
- Tests use **JUnit 5** (Jupiter) — `@Test` + `org.junit.jupiter.api.Assertions`. The H2 integration test (`StoredProcedureStatisticExecutorH2Test`) bootstraps an in-memory persistence unit (`src/test/resources/META-INF/persistence.xml`) and registers stored procedures via H2 `CREATE ALIAS`.
- `lombok` and `slf4j-api` are `provided` scope; `hibernate-core` (using `javax.persistence`, not `jakarta.persistence`) and `jackson-annotations` (only `@JsonFormat` is used) are compile dependencies. Consuming apps supply Lombok/SLF4J themselves.

## Architecture

### Package layout (`io.github.vatisteve.tkbc.db`)

- `generic/` — core contracts implemented by consumers:
  - `Statistic<T>` — one report cell. Holds a typed value and defines `sumNext` (accumulate another cell into this one), `setValue` (assign from a query result), and `newInstance` (create an empty cell of the same kind, used during accumulation). Optional `useResultMapper()` returns a `Consumer<Object[]>` for row-based (multi-column) mapping; optional `joinPreviousGroup()` (default `true`) drives executor grouping (see below).
  - `ModelInfo<I>` — minimal id/code/name descriptor acting as a row's grouping key.
  - `StatisticExecutor` — "run a query and populate a DTO's statistics" abstraction.
  - `StatisticalRequest<T, R extends Temporal>` — time-range request driven by `StatisticType` (OPTION/MONTH/QUARTER/YEAR/FIRST_SIX_MONTH/FIRST_NINE_MONTH); `getFrom()`/`getTo()` resolve into a `TimeDeterminer<R>`.
  - `EnumResponse<L, V>` — label/value contract implemented by the `Quarter` and `StatisticType` enums (Vietnamese display labels, `getValue() == name()`).

- `model/` — data carriers:
  - `StatisticDto<I>` — a report row: `ModelInfo<I> model` + ordered `List<Statistic<?>> statistics` + optional `children`. `fillStatistics(supplier, size)` pre-registers N cells of one type; `addChild`/`sumNextStatistics`/static `sum`/`of` implement parent-accumulates-children aggregation (parallel-by-index, creating fresh instances via `newInstance()` if the parent list starts empty).
  - `StatisticParameter` — name/value/type contract for stored-procedure inputs.
  - `StatisticResponseWrapper` — marker interface + `StatisticResponseWrapperImpl<I, T>` (`ArrayList<T>`) + `collectFor(supplier)` stream collector for assembling response lists.

- `service/sp/` — stored-procedure executor:
  - `StoredProcedureStatisticExecutor` (implements `StatisticExecutor`) — see "Statistic grouping & mapping" below.
  - `StoredProcedureParameterIn<T>` — validated IN parameter (`StatisticParameter`). The two-arg constructor requires a non-null value and derives `getType()` from its runtime class; the three-arg `(name, value, type)` constructor allows a null value with an explicit type (to bind SQL `NULL`).

- `helper/` — built-in `Statistic` implementations: `LongStatistic`, `IntegerStatistic`, `DoubleStatistic` (BigDecimal-backed accumulation to avoid float drift), `BigDecimalStatistic`, `StringStatistic`, `ObjectStatistic`, `TriStateStatistic` (OR-accumulating boolean). `StringStatistic`/`ObjectStatistic` intentionally no-op `sumNext` (just log a warning) since they aren't accumulable. `StatisticHelper.fillStatistics`/`createStatistics` bulk-populate a statistics list from a `Supplier`.

- `query/` — ready-made `StatisticalRequest` implementations: `InstantStatisticalRequest` (`java.time.Instant`) and `LocalDateStatisticalRequest` (String input in `yyyy-MM-dd`, `LocalDateTime` output). Both resolve YEAR/QUARTER/MONTH/FIRST_SIX_MONTH/FIRST_NINE_MONTH/OPTION into concrete from/to bounds using `zoneId()` (defaults to system zone).

- `example/` (under `src/test/java`, so it is **not** published in the jar) — reference implementations (`SampleObjectDataStatistic`, `SampleArrayListDataStatistic`) demonstrating the row-mapper (`useResultMapper`) style for custom multi-column statistic cells.

### Statistic grouping & mapping (`StoredProcedureStatisticExecutor`)

This is the most subtle part of the codebase — read it fully before changing executor behavior:

1. `execute(dto, parameters)` validates inputs via Apache Commons `Validate` (failures surface as `IllegalArgumentException`/NPE), then calls `splitStatisticsType(dto.getStatistics())`.
2. **Grouping**: statistics are partitioned into contiguous `StatisticGroup`s based on each statistic's `joinPreviousGroup()`. A new group starts whenever `joinPreviousGroup()` returns `false`; each group records the `getType()` and `useResultMapper() != null` of its first element. Consumers must keep statistics that belong to the same result set contiguous.
3. **Execution (raw JDBC)**: the executor unwraps the `EntityManager` to a Hibernate `Session` and runs `session.doWork(connection -> …)`. It builds a `{call proc(?, …)}` `CallableStatement`, binds parameters **positionally in list order** (`cs.setObject(i+1, value)`), and calls `cs.execute()`. Parameter names on `StatisticParameter` are not used for binding — the supplied list order is what matters.
4. **Per-group result consumption** (`consumeResults`): each group consumes exactly one result set, in order. Rows are read with raw `ResultSet.getObject(...)` into `List<Object[]>` — deliberately bypassing Hibernate's typed extraction (this is the issue #2 fix: Hibernate reused the first result set's type descriptor for later ones and threw `ArithmeticException: Rounding necessary` when forcing DECIMAL into BIGINT/BigInteger). Then:
   - mapper group: each registered statistic's `useResultMapper()` consumer is applied to its corresponding full row `Object[]`.
   - scalar group: each statistic's `setValue(Object)` is called with the row's first column.
   - `checkStatisticSizeAndLogWarning` logs a warning if the result has more rows than registered statistics, or an error if fewer; mapping iterates only `min(rows, statistics)`.
   - `cs.getMoreResults()` advances to the next result set; if exhausted while groups remain, the rest are skipped with a logged error — the procedure must return one result set per group, in the same order as the registered statistic groups.
