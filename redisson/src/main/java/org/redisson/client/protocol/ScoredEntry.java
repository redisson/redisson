/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.client.protocol;

import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class ScoredEntry<V> implements Comparable<ScoredEntry<V>> {

    private final Double score;
    private final V value;

    public ScoredEntry(Double score, V value) {
        super();
        this.score = score;
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    public Double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoredEntry<?> that = (ScoredEntry<?>) o;
        return Objects.equals(score, that.score) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, value);
    }

    @Override
    public int compareTo(ScoredEntry<V> o) {
        return score.compareTo(o.score);
    }

    @Override
    public String toString() {
        return "ScoredEntry{" +
                "score=" + score +
                ", value=" + value +
                '}';
    }
}
