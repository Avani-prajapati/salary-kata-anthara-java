package com.acme.salary.seed;

import java.util.List;
import java.util.SequencedMap;
import java.util.SplittableRandom;

final class WeightedChoice<T> {

    private final List<T> values;
    private final int[] cumulativeWeights;
    private final int totalWeight;

    WeightedChoice(SequencedMap<T, Integer> weights) {
        this.values = List.copyOf(weights.keySet());
        this.cumulativeWeights = new int[values.size()];
        int runningTotal = 0;
        for (int position = 0; position < values.size(); position++) {
            runningTotal += weights.get(values.get(position));
            cumulativeWeights[position] = runningTotal;
        }
        this.totalWeight = runningTotal;
    }

    T pick(SplittableRandom random) {
        int ticket = random.nextInt(totalWeight);
        int position = 0;
        while (cumulativeWeights[position] <= ticket) {
            position++;
        }
        return values.get(position);
    }
}
