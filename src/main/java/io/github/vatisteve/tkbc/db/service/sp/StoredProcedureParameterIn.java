package io.github.vatisteve.tkbc.db.service.sp;

import io.github.vatisteve.tkbc.db.model.StatisticParameter;
import lombok.Getter;
import org.apache.commons.lang3.Validate;

/**
 * Simple IN parameter implementation for stored procedure executions.
 * <p>
 * Use {@link #StoredProcedureParameterIn(String, Object)} for a non-null value (its type is
 * derived from the value's runtime class), or {@link #StoredProcedureParameterIn(String, Object,
 * Class)} to pass a (possibly {@code null}) value with an explicit type - required when binding
 * SQL {@code NULL} for an optional procedure parameter.
 */
@Getter
public class StoredProcedureParameterIn<T> implements StatisticParameter {

    private final String name;
    private final T value;
    private final Class<?> type;

    public StoredProcedureParameterIn(String name, T value) {
        Validate.notBlank(name, "Parameter name cannot be blank");
        Validate.notNull(value, "Parameter value cannot be null");
        this.name = name;
        this.value = value;
        this.type = value.getClass();
    }

    public StoredProcedureParameterIn(String name, T value, Class<?> type) {
        Validate.notBlank(name, "Parameter name cannot be blank");
        Validate.notNull(type, "Parameter type cannot be null");
        this.name = name;
        this.value = value;
        this.type = type;
    }

}
