package io.github.vatisteve.tkbc.db.example;

import io.github.vatisteve.tkbc.db.example.SampleArrayListDataStatistic.Value;
import io.github.vatisteve.tkbc.db.generic.Statistic;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * @author tinhnv - Jan 24 2025
 */
@Slf4j
public class SampleArrayListDataStatistic implements Statistic<Value> {

    public static class Value extends ArrayList<Object> {
        private static final long serialVersionUID = 2025;
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
        // Not implementation yet
    }

    @Override
    public void setValue(Object other) {
        if (other instanceof Value) {
            value.clear();
            value.addAll((Value) other);
        }
    }

    @Override
    public Consumer<Object[]> useResultMapper() {
        return row -> {
            for(int i = 0; i < row.length; i++) {
                value.set(i, row[i]);
            }
        };
    }

    @Override
    public Statistic<Value> newInstance() {
        return new SampleArrayListDataStatistic();
    }

}
