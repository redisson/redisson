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
package org.redisson.api.bucket;

/**
 * Implementation of {@link CompareAndDeleteArgs}.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public final class CompareAndDeleteParams<V> implements CompareAndDeleteArgs<V> {

    private final ConditionType conditionType;
    private V value;
    private String digest;

    CompareAndDeleteParams(ConditionType conditionType, V object) {
        this.conditionType = conditionType;
        this.value = object;
    }

    CompareAndDeleteParams(ConditionType conditionType, String digest) {
        this.conditionType = conditionType;
        this.digest = digest;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public V getValue() {
        return value;
    }

    public String getDigest() {
        return digest;
    }
}