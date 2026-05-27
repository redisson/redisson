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
package org.redisson.client;

/**
 * Thrown when a Redis connection fails during the bootstrap pipeline —
 * AUTH/HELLO, SELECT, CLIENT SETNAME, READONLY, or the initial PING —
 * before the connection is handed to the pool.
 * <p>
 * The {@link #getPhase()} field identifies which command in the pipeline
 * triggered the failure, so callers (metrics interceptors, circuit breakers,
 * connection-pool init) can distinguish e.g. an auth rejection from a
 * database-select error without parsing the message string.
 * <p>
 * The causing exception (accessible via {@link #getCause()}) preserves the
 * original typed exception, which may itself carry a
 * {@link RedisException#getServerError()} value for server-side errors.
 *
 * @author Nikita Koksharov
 */
public class RedisConnectionBootstrapException extends RedisConnectionException {

    private static final long serialVersionUID = 1L;

    /**
     * The logical phase of the bootstrap pipeline that failed.
     */
    public enum Phase {
        /** AUTH or HELLO AUTH command */
        AUTH,
        /** HELLO command (RESP3 negotiation) */
        HELLO,
        /** SELECT command (database selection) */
        SELECT,
        /** CLIENT SETNAME command */
        CLIENT_SETNAME,
        /** CLIENT CAPA command */
        CLIENT_CAPA,
        /** READONLY command (replica routing) */
        READONLY,
        /** Initial PING (ping connection interval check) */
        PING,
        /** Multiple pipeline steps failed simultaneously, or failure point unknown */
        UNKNOWN
    }

    private final Phase phase;

    public RedisConnectionBootstrapException(String message, Phase phase, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    /**
     * Returns the bootstrap phase that failed.
     *
     * @return phase, never null
     */
    public Phase getPhase() {
        return phase;
    }

}
