package io.github.vatisteve.tkbc.db.helper;

import io.github.vatisteve.tkbc.db.generic.Statistic;
import lombok.extern.slf4j.Slf4j;

/**
 * @author tinhnv - Jan 22 2025
 */
@Slf4j
public class TriStateStatistic implements Statistic<Boolean> {

    private Boolean value;

    @Override
    public Boolean getValue() {
        return value;
    }

    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override
    public void sumNext(Statistic<Boolean> other) {
        Boolean otherValue = other.getValue();
        // Stay null until the first non-null accumulation; treat a null current value as false
        // (a plain `value || otherValue` would unbox the null initial value and throw NPE).
        if (otherValue != null) value = (value != null && value) || otherValue;
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Boolean) {
            this.value = (Boolean) value;
        } else if (value instanceof Number) {
            // Treat 0 as false, any other number as true
            this.value = ((Number) value).intValue() != 0;
        } else if (value instanceof String) {
            String strValue = ((String) value).toLowerCase().trim();
            this.value = strValue.equals("true") || strValue.equals("1") || strValue.equals("yes");
        } else {
            log.warn("{} cannot be assigned to TriState Statistic", value);
        }
    }

    @Override
    public Statistic<Boolean> newInstance() {
        return new TriStateStatistic();
    }

}
