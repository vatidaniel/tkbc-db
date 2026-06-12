package io.github.vatisteve.tkbc.db.model;

import io.github.vatisteve.tkbc.db.generic.ModelInfo;
import io.github.vatisteve.tkbc.db.generic.Statistic;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author tinhnv
 * @since Jan 11, 2024
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = false, of = {"model","statistics"})
public class StatisticDto<I extends Serializable> {

    private final ModelInfo<I> model;
    private final List<Statistic<?>> statistics;
    private List<StatisticDto<I>> children = new ArrayList<>();

    public StatisticDto(ModelInfo<I> model) {
        this.model = model;
        this.statistics = new ArrayList<>();
    }

    public void addStatistics(Statistic<?> statistic) {
        this.statistics.add(statistic);
    }

    public void addChild(StatisticDto<I> child) {
        children.add(child);
        sumNextStatistics(child.statistics);
    }

    public void sumNextStatistics(List<Statistic<?>> children) {
        sum(this.statistics, children);
    }

    public <T extends Statistic<?>> void fillStatistics(Supplier<T> supplier, int size) {
        for (int i = 0; i < size; i++) {
            statistics.add(supplier.get());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Statistic<?>> void sum(List<T> parents, List<T> children) {
        if (parents.isEmpty()) {
            children.forEach(c -> parents.add((T) c.newInstance()));
        }
        if (parents.size() != children.size()) {
            log.warn("Mismatched statistics size while summing: parents [{}] vs children [{}] - extra cells are ignored",
                    parents.size(), children.size());
        }
        int count = Math.min(parents.size(), children.size());
        for (int i = 0; i < count; i++) {
            parents.get(i).sumNext((Statistic) children.get(i));
        }
    }

    public static <I extends Serializable> StatisticDto<I> of(ModelInfo<I> model, List<StatisticDto<I>> children) {
        StatisticDto<I> result = new StatisticDto<>(model);
        children.forEach(c -> sum(result.statistics, c.statistics));
        return result;
    }

}
