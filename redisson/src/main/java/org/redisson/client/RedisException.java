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
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisException extends RuntimeException {

    private static final long serialVersionUID = 3389820652701696154L;

    /**
     * The raw error string returned by the Redis server on the wire (e.g.
     * {@code "ERR Exceeded limit of IAM Authentication requests"}), without
     * the channel/command suffix that appears in {@link #getMessage()}.
     * <p>
     * Null when the exception was not produced from a server error response
     * (e.g. client-side connection failures).
     */
    private final String serverError;

    public RedisException() {
        this.serverError = null;
    }

    public RedisException(Throwable cause) {
        super(cause);
        this.serverError = null;
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause);
        this.serverError = null;
    }

    public RedisException(String message) {
        super(message);
        this.serverError = null;
    }

    /**
     * Constructs an exception with both a full display message and the raw
     * server-error prefix. Use this constructor (or subclass it) when the
     * caller needs to inspect the server error without parsing
     * {@link #getMessage()}.
     *
     * @param message    full display message (may include channel/command context)
     * @param serverError raw error string from the Redis wire protocol
     */
    public RedisException(String message, String serverError) {
        super(message);
        this.serverError = serverError;
    }

    /**
     * Returns the raw error string from the Redis wire protocol, or
     * {@code null} if this exception was not created from a server error
     * response.
     *
     * @return raw server error, e.g. {@code "ERR Exceeded limit of IAM Authentication requests"}
     */
    public String getServerError() {
        return serverError;
    }

}
