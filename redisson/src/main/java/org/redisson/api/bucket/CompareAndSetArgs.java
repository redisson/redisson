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

import java.time.Duration;
import java.time.Instant;

/**
 * Arguments for compare-and-set operation on RBucket.
 * <p>
 * Use one of the static factory methods to create a condition, then call {@code set()} to specify
 * the new value, and optionally configure TTL or expiration time.
 * <p>
 * Supports multiple comparison modes:
 * <ul>
 *   <li>{@link #expected(Object)} - Set if current value equals expected value (compatible with any Redis/Valkey version)</li>
 *   <li>{@link #unexpected(Object)} - Set if current value does not equal unexpected value (compatible with any Redis/Valkey version)</li>
 *   <li>{@link #expectedDigest(String)} - Set if current value's hash digest equals expected digest (requires Redis 8.4+, uses SET IFDEQ)</li>
 *   <li>{@link #unexpectedDigest(String)} - Set if current value's hash digest does not equal unexpected digest (requires Redis 8.4+, uses SET IFDNE)</li>
 * </ul>
 * <p>
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface CompareAndSetArgs<V> {

    /**
     * Creates a condition that succeeds if the current value equals the expected value.
     * <p>
     * This mode is compatible with any Valkey or Redis version.
     *
     * @param <V> value type
     * @param object expected current value (can be null to check for non-existence)
     * @return condition builder requiring {@code set()} to be called
     */
    static <V> CompareAndSetStep<V> expected(V object) {
        return new CompareAndSetParams<V>().expected(object);
    }

    /**
     * Creates a condition that succeeds if the current value does not equal the unexpected value.
     * <p>
     * This mode is compatible with any Valkey or Redis version.
     *
     * @param <V> value type
     * @param object unexpected current value
     * @return condition builder requiring {@code set()} to be called
     */
    static <V> CompareAndSetStep<V> unexpected(V object) {
        return new CompareAndSetParams<V>().unexpected(object);
    }

    /**
     * Creates a condition that succeeds if the hash digest of the current value equals the expected digest.
     * <p>
     * This mode uses the SET IFDEQ command and requires Redis 8.4+ or compatible Valkey version.
     * The digest can be obtained using the DIGEST command.
     *
     * @param <V> value type
     * @param value expected hash digest value (hexadecimal string from DIGEST command)
     * @return condition builder requiring {@code set()} to be called
     */
    static <V> CompareAndSetStep<V> expectedDigest(String value) {
        return new CompareAndSetParams<V>().expectedDigest(value);
    }

    /**
     * Creates a condition that succeeds if the hash digest of the current value does not equal the unexpected digest.
     * <p>
     * This mode uses the SET IFDNE command and requires Redis 8.4+ or compatible Valkey version.
     * The digest can be obtained using the DIGEST command.
     *
     * @param <V> value type
     * @param value unexpected hash digest value (hexadecimal string from DIGEST command)
     * @return condition builder requiring {@code set()} to be called
     */
    static <V> CompareAndSetStep<V> unexpectedDigest(String value) {
        return new CompareAndSetParams<V>().unexpectedDigest(value);
    }

    /**
     * Sets the time-to-live duration for the key.
     * This is optional and can be combined with the set operation.
     *
     * @param duration time-to-live duration
     * @return this instance for method chaining
     */
    CompareAndSetArgs<V> timeToLive(Duration duration);

    /**
     * Sets the expiration time as an absolute instant.
     * This is optional and can be combined with the set operation.
     *
     * @param time expiration instant
     * @return this instance for method chaining
     */
    CompareAndSetArgs<V> expireAt(Instant time);

}