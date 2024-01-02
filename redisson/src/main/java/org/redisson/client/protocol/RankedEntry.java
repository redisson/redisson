/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
public class RankedEntry<V> {

    private final Double score;
    private final Integer rank;

    public RankedEntry(Integer rank, Double score) {
        super();
        this.score = score;
        this.rank = rank;
    }

    public Double getScore() {
        return score;
    }

    public Integer getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RankedEntry<?> that = (RankedEntry<?>) o;
        return Objects.equals(score, that.score) && Objects.equals(rank, that.rank);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, rank);
    }
}
