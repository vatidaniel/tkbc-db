package io.github.vatisteve.tkbc.db.example;

import io.github.vatisteve.tkbc.db.generic.Statistic;
import io.github.vatisteve.tkbc.db.example.SampleObjectDataStatistic.Value;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Example statistic object which implements {@link Statistic} with custom value
 * @author tinhnv - Jan 24 2025
 */
@Slf4j
@SuppressWarnings("unused")
public class SampleObjectDataStatistic implements Statistic<Value> {

    @Data
    public static class Value {
        private int id;
        private String code;
        private String name;
        private String description;
        private int count;
    }
    
    private final Value value = new Value();

    @Override
    public Value getValue() {
        return value;
    }

    @Override
    public Class<Value> getType() {
        return Value.class;
    }

    @Override
    public void sumNext(Statistic<Value> other) {
        Value otherValue = other.getValue();
        value.count += otherValue.count;
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Value) {
            Value otherValue = (Value) value;
            this.value.id = otherValue.id;
            this.value.code = otherValue.code;
            this.value.name = otherValue.name;
            this.value.description = otherValue.description;
            this.value.count = otherValue.count;
        }
    }

    @Override
    public Consumer<Object[]> useResultMapper() {
        return row -> {
            try {
                this.value.id = (int) row[0];
                this.value.code = (String) row[1];
                this.value.name = (String) row[2];
                this.value.description = (String) row[3];
                this.value.count = (int) row[4];
            } catch (Exception e) {
                log.error("Couldn't map row data to {}", this.value.getClass().getSimpleName(), e);
            }
        };
    }

    @Override
    public Statistic<Value> newInstance() {
        return new SampleObjectDataStatistic();
    }

}
