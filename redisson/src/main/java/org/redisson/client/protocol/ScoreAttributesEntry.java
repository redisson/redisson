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
package org.redisson.client.protocol;

import java.util.Objects;

/**
 *
 * @author seakider
 *
 * @param <V> value type
 */
public class ScoreAttributesEntry<V> {
    private final Double score;
    private final V value;
    private final String attributes;

    public ScoreAttributesEntry(Double score, V value, String attributes) {
        super();
        this.score = score;
        this.attributes = attributes;
        this.value = value;
    }

    public Double getScore() {
        return score;
    }

    public String getAttributes() {
        return attributes;
    }

    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreAttributesEntry<?> that = (ScoreAttributesEntry<?>) o;
        return Objects.equals(score, that.score) && Objects.equals(value, that.value) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(score, value, attributes);
    }

    @Override
    public String toString() {
        return "ScoreAttributesEntry{" +
                "score=" + score +
                ", value=" + value +
                ", attributes=" + attributes +
                '}';
    }
}
