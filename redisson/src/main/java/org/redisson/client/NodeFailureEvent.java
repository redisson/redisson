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

import org.redisson.api.NodeType;

import java.net.InetSocketAddress;

/**
 * Structured Redisson node failure signal emitted from lifecycle paths that do not fit the legacy
 * binary {@link FailedNodeDetector} callbacks.
 *
 * @author Nikita Koksharov
 */
public final class NodeFailureEvent {

    private final InetSocketAddress address;
    private final NodeType nodeType;
    private final NodeFailureStage stage;
    private final NodeFailureCategory category;
    private final NodeFailureConnectionType connectionType;
    private final String commandName;
    private final int attempt;
    private final int attempts;
    private final Throwable cause;

    public NodeFailureEvent(InetSocketAddress address, NodeType nodeType, NodeFailureStage stage,
                            NodeFailureCategory category, NodeFailureConnectionType connectionType,
                            String commandName, int attempt, int attempts, Throwable cause) {
        this.address = address;
        this.nodeType = nodeType;
        this.stage = stage;
        this.category = category;
        this.connectionType = connectionType;
        this.commandName = commandName;
        this.attempt = attempt;
        this.attempts = attempts;
        this.cause = cause;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public NodeFailureStage getStage() {
        return stage;
    }

    public NodeFailureCategory getCategory() {
        return category;
    }

    public NodeFailureConnectionType getConnectionType() {
        return connectionType;
    }

    public String getCommandName() {
        return commandName;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Throwable getCause() {
        return cause;
    }
}
