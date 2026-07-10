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
 * Thrown when a Redis server rejects a command due to server-side resource limits or
 * rate throttling — as opposed to a protocol error, cluster redirect, or auth failure.
 * <p>
 * Examples of server-side rejections covered by this exception:
 * <ul>
 *   <li>Max-clients limit reached ({@code ERR max number of clients reached})</li>
 *   <li>IAM authentication rate limit from AWS ElastiCache
 *       ({@code ERR Exceeded limit of IAM Authentication requests})</li>
 *   <li>Generic server-side rate limits or resource-exhaustion errors not matched
 *       by any other typed {@link RedisException} subclass</li>
 * </ul>
 * <p>
 * Use {@link #getReason()} to branch on the specific cause without parsing the message
 * string. Use {@link #getServerError()} (inherited from {@link RedisException}) to access
 * the raw wire error for logging or metrics.
 */
public class RedisServerRejectionException extends RedisException {

    private static final long serialVersionUID = 1L;

    /**
     * Broad category of the server-side rejection reason.
     */
    public enum Reason {
        /**
         * Redis's configured {@code maxclients} limit was reached.
         * Wire error typically starts with {@code "ERR max number of clients reached"}.
         */
        MAX_CLIENTS,

        /**
         * AWS ElastiCache IAM authentication request rate limit was exceeded.
         * Wire error typically starts with {@code "ERR Exceeded limit of IAM Authentication requests"}.
         */
        IAM_THROTTLE,

        /**
         * A server-side rate limit or resource quota was hit, but not one of the
         * specifically enumerated cases above.
         */
        RATE_LIMITED,

        /**
         * Server-side rejection that does not match any recognized pattern.
         * Inspect {@link RedisServerRejectionException#getServerError()} for details.
         */
        OTHER
    }

    private final Reason reason;

    public RedisServerRejectionException(String message, String serverError, Reason reason) {
        super(message, serverError);
        this.reason = reason;
    }

    /**
     * Returns the classified rejection reason. Never null.
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * Classifies a raw Redis wire-error string into a {@link Reason}.
     * The wire error is the text that follows the {@code '-'} prefix in the RESP protocol,
     * before any channel/command context is appended.
     *
     * @param serverError the raw server error string; must not be null
     * @return the matching {@link Reason}, never null
     */
    public static Reason classifyReason(String serverError) {
        if (serverError == null) {
            return Reason.OTHER;
        }
        // Normalize to lowercase for case-insensitive matching
        String lower = serverError.toLowerCase();

        if (lower.contains("max number of clients reached")
                || lower.contains("maxclients")) {
            return Reason.MAX_CLIENTS;
        }
        if (lower.contains("exceeded limit of iam authentication")
                || lower.contains("iam authentication requests")) {
            return Reason.IAM_THROTTLE;
        }
        if (lower.contains("rate limit")
                || lower.contains("ratelimit")
                || lower.contains("too many connections")
                || lower.contains("too many requests")
                || lower.contains("request limit")) {
            return Reason.RATE_LIMITED;
        }
        return Reason.OTHER;
    }
}
