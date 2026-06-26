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

import org.redisson.RedissonShutdownException;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.misc.RedisURI;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Best-effort reporter for structured node failure events. Reporting must never affect Redis
 * command execution, connection management, or reconnection behavior.
 *
 * @author Nikita Koksharov
 */
public final class NodeFailureReporter {

    private NodeFailureReporter() {
    }

    public static void report(RedisClient client, NodeFailureStage stage, Throwable cause) {
        report(client, stage, NodeFailureConnectionType.UNKNOWN, null, cause, -1, -1);
    }

    public static void report(RedisClient client, NodeFailureStage stage,
                              NodeFailureConnectionType connectionType, Throwable cause) {
        report(client, stage, connectionType, null, cause, -1, -1);
    }

    public static void report(RedisClient client, NodeFailureStage stage, RedisCommand<?> command,
                              Throwable cause, int attempt, int attempts) {
        report(client, stage, NodeFailureConnectionType.UNKNOWN, command, cause, attempt, attempts);
    }

    public static void report(RedisClient client, NodeFailureStage stage,
                              NodeFailureConnectionType connectionType, RedisCommand<?> command,
                              Throwable cause, int attempt, int attempts) {
        if (client == null || client.getConfig() == null || client.getConfig().getFailedNodeDetector() == null) {
            return;
        }

        try {
            Throwable unwrapped = unwrap(cause);
            NodeFailureEvent event = new NodeFailureEvent(
                    resolveAddress(client),
                    client.getConfig().getNodeType(),
                    stage,
                    classify(unwrapped),
                    connectionType != null ? connectionType : NodeFailureConnectionType.UNKNOWN,
                    command != null ? command.getName() : null,
                    attempt,
                    attempts,
                    unwrapped);
            client.getConfig().getFailedNodeDetector().onNodeFailure(event);
        } catch (Exception ignored) {
            // Failure reporting is observational only.
        }
    }

    private static InetSocketAddress resolveAddress(RedisClient client) {
        try {
            InetSocketAddress addr = client.getAddr();
            if (addr != null) {
                return addr;
            }
        } catch (Exception ignored) {
            // fall back to unresolved URI below
        }

        RedisURI uri = client.getConfig().getAddress();
        if (uri == null) {
            return null;
        }
        return InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
    }

    public static NodeFailureCategory classify(Throwable cause) {
        Throwable unwrapped = unwrap(cause);
        if (unwrapped == null) {
            return NodeFailureCategory.UNKNOWN;
        }

        Throwable rootCause = rootCause(unwrapped);
        String message = unwrapped.getMessage();
        String rootMessage = rootCause != null ? rootCause.getMessage() : null;
        String normalizedMessage = ((message != null ? message : "") + " " + (rootMessage != null ? rootMessage : ""))
                .toLowerCase(Locale.ROOT);
        if (normalizedMessage.contains("max number of clients")
                || normalizedMessage.contains("maxclients")
                || normalizedMessage.contains("max clients")) {
            return NodeFailureCategory.MAX_CLIENTS;
        }
        if (normalizedMessage.contains("throttl")
                && (normalizedMessage.contains("auth")
                        || normalizedMessage.contains("iam")
                        || normalizedMessage.contains("credential"))) {
            return NodeFailureCategory.AUTH_THROTTLE;
        }
        if (normalizedMessage.contains("unable to acquire connection")
                || normalizedMessage.contains("increase connection pool size")) {
            return NodeFailureCategory.POOL_EXHAUSTED;
        }

        if (unwrapped instanceof RedisWrongPasswordException) {
            return NodeFailureCategory.WRONG_PASSWORD;
        }
        if (unwrapped instanceof RedisAuthRequiredException) {
            return NodeFailureCategory.AUTH;
        }
        if (unwrapped instanceof RedisResponseTimeoutException) {
            return NodeFailureCategory.RESPONSE_TIMEOUT;
        }
        if (unwrapped instanceof RedisTimeoutException) {
            return NodeFailureCategory.TIMEOUT;
        }
        if (unwrapped instanceof WriteRedisConnectionException) {
            return NodeFailureCategory.WRITE;
        }
        if (rootCause instanceof ConnectException) {
            return NodeFailureCategory.CONNECTION_REFUSED;
        }
        if (rootCause instanceof SocketTimeoutException) {
            return NodeFailureCategory.CONNECT_TIMEOUT;
        }
        if (rootCause instanceof UnknownHostException) {
            return NodeFailureCategory.DNS_RESOLUTION;
        }
        if (unwrapped instanceof RedisConnectionException) {
            return NodeFailureCategory.CONNECTION;
        }
        if (unwrapped instanceof RedisMovedException) {
            return NodeFailureCategory.MOVED;
        }
        if (unwrapped instanceof RedisAskException) {
            return NodeFailureCategory.ASK;
        }
        if (unwrapped instanceof RedisReadonlyException) {
            return NodeFailureCategory.READONLY;
        }
        if (unwrapped instanceof RedisLoadingException) {
            return NodeFailureCategory.LOADING;
        }
        if (unwrapped instanceof RedisClusterDownException) {
            return NodeFailureCategory.CLUSTER_DOWN;
        }
        if (unwrapped instanceof RedisTryAgainException) {
            return NodeFailureCategory.TRY_AGAIN;
        }
        if (unwrapped instanceof RedisBusyException) {
            return NodeFailureCategory.BUSY;
        }
        if (unwrapped instanceof RedisMasterDownException) {
            return NodeFailureCategory.MASTER_DOWN;
        }
        if (unwrapped instanceof RedisNoReplicasException) {
            return NodeFailureCategory.NO_REPLICAS;
        }
        if (unwrapped instanceof RedisOutOfMemoryException) {
            return NodeFailureCategory.OUT_OF_MEMORY;
        }
        if (unwrapped instanceof RedisNoScriptException) {
            return NodeFailureCategory.NO_SCRIPT;
        }
        if (unwrapped instanceof RedisReconnectedException) {
            return NodeFailureCategory.RECONNECTED;
        }
        if (unwrapped instanceof RedisRetryException) {
            return NodeFailureCategory.REDIS_RETRY;
        }
        if (unwrapped instanceof RedissonShutdownException) {
            return NodeFailureCategory.SHUTDOWN;
        }
        if (unwrapped instanceof RedisException) {
            return NodeFailureCategory.REDIS_SERVER_ERROR;
        }
        if (unwrapped instanceof CancellationException) {
            return NodeFailureCategory.CANCELLED;
        }
        return NodeFailureCategory.UNKNOWN;
    }

    private static Throwable unwrap(Throwable cause) {
        Throwable result = cause;
        while (result instanceof CompletionException && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static Throwable rootCause(Throwable cause) {
        Throwable result = unwrap(cause);
        while (result != null && result.getCause() != null && result.getCause() != result) {
            result = unwrap(result.getCause());
        }
        return result;
    }
}
