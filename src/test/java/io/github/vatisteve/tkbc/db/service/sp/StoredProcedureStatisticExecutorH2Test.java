package io.github.vatisteve.tkbc.db.service.sp;

import io.github.vatisteve.tkbc.db.generic.ModelInfo;
import io.github.vatisteve.tkbc.db.generic.Statistic;
import io.github.vatisteve.tkbc.db.helper.BigDecimalStatistic;
import io.github.vatisteve.tkbc.db.helper.DoubleStatistic;
import io.github.vatisteve.tkbc.db.helper.LongStatistic;
import io.github.vatisteve.tkbc.db.model.StatisticDto;
import lombok.Getter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test (H2 in-memory) covering the raw-JDBC result extraction that fixes issue #2:
 * a DECIMAL result set must map into the registered statistics without going through Hibernate's
 * BigInteger type coercion (which previously threw {@code ArithmeticException: Rounding necessary}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoredProcedureStatisticExecutorH2Test {

    private EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    void setUp() {
        emf = Persistence.createEntityManagerFactory("tkbc-test");
        em = emf.createEntityManager();
        em.getTransaction().begin();
        em.unwrap(Session.class).doWork(connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE ALIAS IF NOT EXISTS DECIMAL_SERIES FOR \""
                        + H2TestProcedures.class.getName() + ".decimalSeries\"");
                st.execute("CREATE ALIAS IF NOT EXISTS SCALED_DECIMAL FOR \""
                        + H2TestProcedures.class.getName() + ".scaledDecimal\"");
            }
        });
        em.getTransaction().commit();
    }

    @AfterAll
    void tearDown() {
        if (em != null && em.isOpen()) em.close();
        if (emf != null && emf.isOpen()) emf.close();
    }

    @Test
    void decimalResultSetMapsIntoLongWithoutArithmeticException() {
        StatisticDto<Long> dto = new StatisticDto<>(model(1L));
        dto.addStatistics(new LongStatistic());
        dto.addStatistics(new LongStatistic());

        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("DECIMAL_SERIES", em);
        assertDoesNotThrow(() -> exec.execute(dto, Collections.emptyList()));

        List<Statistic<?>> stats = dto.getStatistics();
        assertEquals(123L, stats.get(0).getValue());
        assertEquals(678L, stats.get(1).getValue());
    }

    @Test
    void decimalResultSetPreservesValueInBigDecimalStatistic() {
        StatisticDto<Long> dto = new StatisticDto<>(model(2L));
        dto.addStatistics(new BigDecimalStatistic());
        dto.addStatistics(new BigDecimalStatistic());

        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("DECIMAL_SERIES", em);
        exec.execute(dto, Collections.emptyList());

        List<Statistic<?>> stats = dto.getStatistics();
        assertEquals(0, new BigDecimal("123.45").compareTo((BigDecimal) stats.get(0).getValue()));
        assertEquals(0, new BigDecimal("678.90").compareTo((BigDecimal) stats.get(1).getValue()));
    }

    @Test
    void positionalParameterIsBoundToProcedure() {
        StatisticDto<Long> dto = new StatisticDto<>(model(3L));
        dto.addStatistics(new DoubleStatistic());

        StoredProcedureStatisticExecutor exec = new StoredProcedureStatisticExecutor("SCALED_DECIMAL", em);
        exec.execute(dto, Collections.singletonList(new StoredProcedureParameterIn<>("FACTOR", 3)));

        assertEquals(31.5, (Double) dto.getStatistics().get(0).getValue(), 0.0001);
    }

    private static ModelInfo<Long> model(Long id) {
        return new SimpleModelInfo<>(id, "C" + id, "Name" + id);
    }

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
}
