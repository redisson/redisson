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
package org.redisson.api.queue;

/**
 * Defines the synchronization modes used for replication with Valkey or Redis replica instances.
 *
 * @author Nikita Koksharov
 *
 */
public enum SyncMode {

    /**
     * Ensures data durability by blocking until write operations are confirmed as persisted to the
     * memory and the Append-Only File (AOF) on the primary Redis instance and replicas if the AOF persistence feature is enabled.
     * If AOF persistence is unavailable, falls back to blocking until replica instances acknowledge
     * that write operations have been applied to memory.
     * If neither durability mechanism is available, proceeds without synchronization guarantees.
     *
     */
    AUTO,

    /**
     * Ensures data durability by blocking until replica instances acknowledge that write operations have been applied to memory.
     */
    ACK,

    /**
     * Ensures data durability by blocking until write operations are confirmed as persisted to the
     * Append-Only File (AOF) on the primary Redis instance and replicas.
     * <p>
     * NOTE: Redis 7.2.0+ or any Valkey version is required
     *
     */
    ACK_AOF

}
