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

import java.util.Objects;

/**
 * Arguments for {@link org.redisson.api.RBucket#compareAndDelete(CompareAndDeleteArgs)} method.
 * Defines conditions for conditional deletion of bucket value.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface CompareAndDeleteArgs<V> {

    /**
     * Deletes bucket if stored value does not equal specified object.
     * Compatible with any Valkey or Redis version.
     *
     * @param object value to compare
     * @param <V> value type
     * @return arguments object
     */
    static <V> CompareAndDeleteArgs<V> unexpected(V object) {
        return new CompareAndDeleteParams<>(ConditionType.UNEXPECTED, object);
    }

    /**
     * Deletes bucket if stored value equals specified object.
     * Compatible with any Valkey or Redis version.
     *
     * @param object value to compare
     * @param <V> value type
     * @return arguments object
     */
    static <V> CompareAndDeleteArgs<V> expected(V object) {
        return new CompareAndDeleteParams<>(ConditionType.EXPECTED, object);
    }

    /**
     * Deletes bucket if stored value's digest equals specified digest.
     * Uses DELEX IFDEQ command. Requires Valkey 8+ or Redis 8.4+.
     *
     * @param value digest value (hexadecimal string from DIGEST command)
     * @param <V> value type
     * @return arguments object
     */
    static <V> CompareAndDeleteArgs<V> expectedDigest(String value) {
        Objects.requireNonNull(value, "Digest value can't be null");
        return new CompareAndDeleteParams<>(ConditionType.EXPECTED_DIGEST, value);
    }

    /**
     * Deletes bucket if stored value's digest does not equal specified digest.
     * Uses DELEX IFDNE command. Requires Valkey 8+ or Redis 8.4+.
     *
     * @param value digest value (hexadecimal string from DIGEST command)
     * @param <V> value type
     * @return arguments object
     */
    static <V> CompareAndDeleteArgs<V> unexpectedDigest(String value) {
        Objects.requireNonNull(value, "Digest value can't be null");
        return new CompareAndDeleteParams<>(ConditionType.UNEXPECTED_DIGEST, value);
    }

}