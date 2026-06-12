package io.github.vatisteve.tkbc.db.service.sp;

import org.h2.tools.SimpleResultSet;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Types;

/**
 * H2 {@code CREATE ALIAS} stored-procedure bodies used by {@link StoredProcedureStatisticExecutorH2Test}.
 * Each method returns a single result set built with {@link SimpleResultSet}.
 */
public final class H2TestProcedures {

    private H2TestProcedures() {
    }

    /** Single result set with a DECIMAL column carrying fractional values (reproduces issue #2 column type). */
    public static ResultSet decimalSeries() {
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("VAL", Types.DECIMAL, 20, 2);
        rs.addRow(new BigDecimal("123.45"));
        rs.addRow(new BigDecimal("678.90"));
        return rs;
    }

    /** Parameterised result set: a single DECIMAL value scaled by the given factor (null defaults to 1). */
    public static ResultSet scaledDecimal(Integer factor) {
        int f = factor == null ? 1 : factor;
        SimpleResultSet rs = new SimpleResultSet();
        rs.addColumn("VAL", Types.DECIMAL, 20, 2);
        rs.addRow(new BigDecimal("10.50").multiply(BigDecimal.valueOf(f)));
        return rs;
    }
}
