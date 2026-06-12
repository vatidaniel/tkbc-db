package io.github.vatisteve.tkbc.db.service.sp;

import io.github.vatisteve.tkbc.db.generic.ModelInfo;
import io.github.vatisteve.tkbc.db.generic.Statistic;
import io.github.vatisteve.tkbc.db.model.StatisticDto;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validation-focused tests for StoredProcedureStatisticExecutor
 */
class StoredProcedureStatisticExecutorValidationTest {

    private EntityManager dummyEntityManager() {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class[]{EntityManager.class},
                (proxy, method, args) -> { throw new UnsupportedOperationException("Not implemented in test"); }
        );
    }

    @Test
    void testConstructorRejectsEmptyProcedureName() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoredProcedureStatisticExecutor("", dummyEntityManager()));
    }

    @Test
    void testConstructorRejectsNullEntityManager() {
        // Apache Validate may throw NPE for null argument; accept any runtime exception
        assertThrows(RuntimeException.class,
                () -> new StoredProcedureStatisticExecutor("proc", null));
    }

    @Test
    void testExecuteRejectsNullParameters() {
        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("proc", dummyEntityManager());
        StatisticDto<Long> dto = new StatisticDto<>(new SimpleModelInfo<>(1L, "C1", "Name1"));
        assertThrows(RuntimeException.class, () -> exec.execute(dto, null));
    }

    @Test
    void testExecuteRejectsNullCursor() {
        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("proc", dummyEntityManager());
        assertThrows(RuntimeException.class, () -> exec.execute(null, Collections.emptyList()));
    }

    @Test
    void testExecuteRejectsNullStatisticsFromCursor() {
        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("proc", dummyEntityManager());
        NullStatisticDto<Long> dto = new NullStatisticDto<>(new SimpleModelInfo<>(2L, "C2", "Name2"));
        assertThrows(RuntimeException.class, () -> exec.execute(dto, Collections.emptyList()));
    }

    // --- Helpers ---

    @Getter
    private static class SimpleModelInfo<I extends Serializable> implements ModelInfo<I> {
        private final I id;
        private final String code;
        private final String name;

        private SimpleModelInfo(I id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

    }

    private static class NullStatisticDto<I extends Serializable> extends StatisticDto<I> {
        public NullStatisticDto(ModelInfo<I> model) {
            super(model);
        }

        @Override
        public List<Statistic<?>> getStatistics() {
            return null; // Simulate bad DTO state
        }
    }
}
