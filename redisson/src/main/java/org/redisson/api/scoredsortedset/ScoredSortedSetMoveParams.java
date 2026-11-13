/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api.scoredsortedset;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class ScoredSortedSetMoveParams implements ScoredSortedSetMoveSelection,
                                                        ScoredSortedSetMoveEndArgs,
                                                        ScoredSortedSetMoveRangeArgs,
                                                        ScoredSortedSetMoveValuesArgs {

    public enum Selector {MIN, MAX, BYSCORE, MEMBERS}

    public enum Condition {NX, XX}

    private final String destName;
    private Selector selector;
    private double startScore;
    private boolean startScoreInclusive;
    private double endScore;
    private boolean endScoreInclusive;
    private Integer count;
    private Collection<?> values;
    private Condition condition;

    ScoredSortedSetMoveParams(String destName) {
        this.destName = Objects.requireNonNull(destName, "name can't be null");
    }

    @Override
    public ScoredSortedSetMoveEndArgs first() {
        selector = Selector.MIN;
        return this;
    }

    @Override
    public ScoredSortedSetMoveEndArgs last() {
        selector = Selector.MAX;
        return this;
    }

    @Override
    public ScoredSortedSetMoveRangeArgs scoreRange(double startScore, boolean startScoreInclusive,
                                                   double endScore, boolean endScoreInclusive) {
        selector = Selector.BYSCORE;
        this.startScore = startScore;
        this.startScoreInclusive = startScoreInclusive;
        this.endScore = endScore;
        this.endScoreInclusive = endScoreInclusive;
        return this;
    }

    @Override
    public ScoredSortedSetMoveValuesArgs values(Object... values) {
        Objects.requireNonNull(values, "values can't be null");
        return values(Arrays.asList(values));
    }

    @Override
    public ScoredSortedSetMoveValuesArgs values(Collection<?> values) {
        selector = Selector.MEMBERS;
        this.values = Objects.requireNonNull(values, "values can't be null");
        return this;
    }

    @Override
    public ScoredSortedSetMoveParams count(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count should be greater than zero");
        }
        this.count = count;
        return this;
    }

    @Override
    public ScoredSortedSetMoveArgs ifAbsent() {
        condition = Condition.NX;
        return this;
    }

    @Override
    public ScoredSortedSetMoveArgs ifExists() {
        condition = Condition.XX;
        return this;
    }

    public String getDestName() {
        return destName;
    }

    public Selector getSelector() {
        return selector;
    }

    public double getStartScore() {
        return startScore;
    }

    public boolean isStartScoreInclusive() {
        return startScoreInclusive;
    }

    public double getEndScore() {
        return endScore;
    }

    public boolean isEndScoreInclusive() {
        return endScoreInclusive;
    }

    public Integer getCount() {
        return count;
    }

    public Collection<?> getValues() {
        return values;
    }

    public Condition getCondition() {
        return condition;
    }

}
