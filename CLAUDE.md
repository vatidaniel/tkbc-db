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
- Java source/target is **1.8**.
- Tests use **JUnit 3** (`junit.framework.TestCase`) — new tests should extend `TestCase` and use `testXxx()` method names, not JUnit 4/5 annotations.
- `lombok` and `slf4j-api` are `provided` scope; `hibernate-core` (using `javax.persistence`, not `jakarta.persistence`) and `jackson-databind` are compile dependencies. Consuming apps supply Lombok/SLF4J themselves.

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
  - `StoredProcedureParameterIn<T>` — validated IN parameter (`StatisticParameter`); its `getType()` is derived from the value's runtime class.

- `helper/` — built-in `Statistic` implementations: `LongStatistic`, `IntegerStatistic`, `DoubleStatistic` (BigDecimal-backed accumulation to avoid float drift), `BigDecimalStatistic`, `StringStatistic`, `ObjectStatistic`, `TriStateStatistic` (OR-accumulating boolean). `StringStatistic`/`ObjectStatistic` intentionally no-op `sumNext` (just log a warning) since they aren't accumulable. `StatisticHelper.fillStatistics`/`createStatistics` bulk-populate a statistics list from a `Supplier`.

- `query/` — ready-made `StatisticalRequest` implementations: `InstantStatisticalRequest` (`java.time.Instant`) and `LocalDateStatisticalRequest` (String input in `yyyy-MM-dd`, `LocalDateTime` output). Both resolve YEAR/QUARTER/MONTH/FIRST_SIX_MONTH/FIRST_NINE_MONTH/OPTION into concrete from/to bounds using `zoneId()` (defaults to system zone).

- `example/` — reference implementations (`SampleObjectDataStatistic`, `SampleArrayListDataStatistic`) demonstrating the row-mapper (`useResultMapper`) style for custom multi-column statistic cells.

### Statistic grouping & mapping (`StoredProcedureStatisticExecutor`)

This is the most subtle part of the codebase — read it fully before changing executor behavior:

1. `execute(dto, parameters)` validates inputs via Apache Commons `Validate` (failures surface as `IllegalArgumentException`/NPE), then calls `splitStatisticsType(dto.getStatistics())`.
2. **Grouping**: statistics are partitioned into contiguous `StatisticGroup`s based on each statistic's `joinPreviousGroup()`. A new group starts whenever `joinPreviousGroup()` returns `false`; each group records the `getType()` and `useResultMapper() != null` of its first element. Consumers must keep statistics with the same type/mapper-presence contiguous so they land in the same JDBC result set.
3. **Execution**: a single `StoredProcedureQuery` is built (all IN parameters registered via `StoredProcedureParameterIn`) and run with `sp.execute()`.
4. **Per-group result consumption** (`getResult`): each group consumes exactly one result set, in order:
   - If the group `useMapper`s, the result set is read as `List<Object[]>` and each registered statistic's `useResultMapper()` consumer is applied to its corresponding row.
   - Otherwise, the result set is read as `List<?>` and each statistic's `setValue(Object)` is called with the corresponding scalar.
   - `checkStatisticSizeAndLogWarning` logs a warning if the result has more rows than registered statistics, or an error if fewer.
   - If `sp.hasMoreResults()` is false but groups remain, the rest are skipped with a logged error — i.e. the stored procedure must return exactly one result set per group, in the same order as the registered statistic groups.
