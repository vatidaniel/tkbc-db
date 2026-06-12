package io.github.vatisteve.tkbc.db.service.sp;

import io.github.vatisteve.tkbc.db.generic.Statistic;
import io.github.vatisteve.tkbc.db.generic.StatisticExecutor;
import io.github.vatisteve.tkbc.db.model.StatisticDto;
import io.github.vatisteve.tkbc.db.model.StatisticParameter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import java.io.Serializable;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * StatisticExecutor implementation that reads statistics from a database Stored Procedure.
 * <p>
 * Parameters are bound positionally (in the order of the supplied list) and each result set is
 * read through the raw JDBC {@link ResultSet} via {@code getObject}, leaving value conversion to
 * the registered {@link io.github.vatisteve.tkbc.db.generic.Statistic} implementations. This
 * deliberately bypasses Hibernate's typed result-set extraction, which - for an untyped procedure
 * call returning multiple result sets - reuses the first result set's type descriptor for the
 * later ones and can throw {@code ArithmeticException: Rounding necessary} when a DECIMAL result
 * set is coerced into a BIGINT/BigInteger (see issue #2).
 * <p>
 * Groups of statistics are split by {@link Statistic#joinPreviousGroup()}; each group consumes one
 * result set in sequence.
 * @author tinhnv - Jan 19 2025
 */
@Slf4j
public class StoredProcedureStatisticExecutor implements StatisticExecutor {

    private final String procedureName;
    private final EntityManager entityManager;

    public StoredProcedureStatisticExecutor(String procedureName, EntityManager entityManager) {
        Validate.notEmpty(procedureName, "procedureName must not be empty");
        Validate.notNull(entityManager, "entityManager must not be null");
        this.procedureName = procedureName;
        this.entityManager = entityManager;
    }

    @Override
    public <I extends Serializable, R extends StatisticDto<I>, P extends StatisticParameter> void execute(R cursor, List<P> parameters) {
        Validate.notNull(parameters, "parameters must not be null");
        Validate.notNull(cursor, "cursor must not be null");
        Validate.notNull(cursor.getStatistics(), "cursor statistics must not be null");
        List<StatisticGroup> groups = splitStatisticsType(cursor.getStatistics());
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(buildCallString(parameters.size()))) {
                for (int i = 0; i < parameters.size(); i++) {
                    StatisticParameter param = parameters.get(i);
                    log.trace("Set Stored Procedure parameter [{}] at index [{}] with value [{}]", param.getName(), i + 1, param.getValue());
                    cs.setObject(i + 1, param.getValue());
                }
                if (cs.execute()) {
                    consumeResults(groups, cs);
                } else {
                    log.error("Execute stored procedure [{}] returned no result set", procedureName);
                }
            }
        });
    }

    private String buildCallString(int parameterCount) {
        StringBuilder sb = new StringBuilder("{call ").append(procedureName).append('(');
        for (int i = 0; i < parameterCount; i++) {
            if (i > 0) sb.append(", ");
            sb.append('?');
        }
        return sb.append(")}").toString();
    }

    private void consumeResults(List<StatisticGroup> groups, CallableStatement cs) throws SQLException {
        boolean hasResult = true;
        for (int g = 0; g < groups.size(); g++) {
            if (!hasResult) {
                log.error("Stored procedure [{}] returned fewer result sets than statistic groups; {} remaining group(s) omitted",
                        procedureName, groups.size() - g);
                break;
            }
            try (ResultSet rs = cs.getResultSet()) {
                assignGroup(groups.get(g), readRows(rs));
            }
            hasResult = cs.getMoreResults();
        }
    }

    private static List<Object[]> readRows(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Object[]> rows = new ArrayList<>();
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int c = 0; c < columnCount; c++) {
                row[c] = rs.getObject(c + 1);
            }
            rows.add(row);
        }
        return rows;
    }

    private static void assignGroup(StatisticGroup group, List<Object[]> rows) {
        List<Statistic<?>> registeredStatistics = group.getStatistics();
        checkStatisticSizeAndLogWarning(group, rows, registeredStatistics);
        int count = Math.min(rows.size(), registeredStatistics.size());
        for (int i = 0; i < count; i++) {
            Statistic<?> stat = registeredStatistics.get(i);
            Object[] row = rows.get(i);
            if (group.useMapper) {
                log.trace("Registered statistics index [{}] mapped with [{}] field(s)", i, row.length);
                stat.useResultMapper().accept(row);
            } else {
                Object value = row.length > 0 ? row[0] : null;
                log.trace("Registered statistics index [{}] set value [{}] with type [{}]", i, value, stat.getType());
                stat.setValue(value);
            }
        }
    }

    private static void checkStatisticSizeAndLogWarning(StatisticGroup group, List<?> queriedStatistics, List<Statistic<?>> registeredStatistics) {
        if (queriedStatistics.size() > registeredStatistics.size()) {
            log.warn("The queried statistics data result size is greater than the registered one [{}/{}] - Query group [{}] ",
                    queriedStatistics.size(), registeredStatistics.size(), group.getType());
        } else if (queriedStatistics.size() < registeredStatistics.size()) {
            log.error("The queried statistics data result size is less than the registered one [{}/{}] - Query group [{}]",
                    queriedStatistics.size(), registeredStatistics.size(), group.getType());
        }
    }

    private List<StatisticGroup> splitStatisticsType(List<Statistic<?>> statistics) {
        List<StatisticGroup> groups = new ArrayList<>();
        if (statistics.isEmpty()) return groups;
        Statistic<?> first = statistics.get(0);
        StatisticGroup group = new StatisticGroup(first);
        group.add(first);
        for (int i = 1; i < statistics.size(); i++) {
            Statistic<?> current = statistics.get(i);
            if (current.joinPreviousGroup()) {
                group.getStatistics().add(current);
            } else {
                groups.add(group);
                group = new StatisticGroup(current);
                group.add(current);
            }
        }
        groups.add(group);
        return groups;
    }

    @Value
    private static class StatisticGroup {
        Class<?> type;
        boolean useMapper;
        List<Statistic<?>> statistics = new ArrayList<>();
        public StatisticGroup(Statistic<?> init) {
            this.type = init.getType();
            this.useMapper = init.useResultMapper() != null;
        }
        public void add(Statistic<?> statistic) {
            statistics.add(statistic);
        }
    }

}
