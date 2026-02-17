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
package org.redisson.api.stream;

/**
 * Arguments object for idempotent stream production.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamIdempotentArgs<T> {

    /**
     * Defines IDMPAUTO mode. Redis automatically generates a unique
     * idempotent ID based on the message content for the producer.
     * <p>
     * Requires <b>Redis 8.6.0 and higher.</b>
     *
     * @return arguments object
     */
    T autoId();

    /**
     * Defines IDMP mode with an explicit idempotent ID for the producer.
     * If this producer/idempotent ID combination was already used,
     * the command returns the ID of the existing entry instead of
     * creating a duplicate.
     * <p>
     * Requires <b>Redis 8.6.0 and higher.</b>
     *
     * @param idempotentId - idempotent identifier
     * @return arguments object
     */
    T idempotentId(String idempotentId);

}