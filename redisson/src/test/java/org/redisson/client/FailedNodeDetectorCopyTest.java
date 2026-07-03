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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FailedNodeDetectorCopyTest {

    @Test
    public void testRedisClientConfigCopyIsolatesDetectorPerNode() throws InterruptedException {
        // A single detector is configured once and reused for every node's RedisClient,
        // each of which copies the base config. The copies must not share failure state,
        // otherwise one failing node marks healthy nodes as failed (issue #7231).
        FailedConnectionDetector detector = new FailedConnectionDetector(50);

        RedisClientConfig config = new RedisClientConfig();
        config.setFailedNodeDetector(detector);

        RedisClientConfig nodeA = new RedisClientConfig(config);
        RedisClientConfig nodeB = new RedisClientConfig(config);

        assertThat(nodeA.getFailedNodeDetector()).isNotSameAs(detector);
        assertThat(nodeA.getFailedNodeDetector()).isNotSameAs(nodeB.getFailedNodeDetector());

        nodeA.getFailedNodeDetector().onConnectFailed(new RuntimeException());
        Thread.sleep(60);

        assertThat(nodeA.getFailedNodeDetector().isNodeFailed()).isTrue();
        assertThat(nodeB.getFailedNodeDetector().isNodeFailed()).isFalse();
    }

    @Test
    public void testFailedConnectionDetectorCopyResetsState() throws InterruptedException {
        FailedConnectionDetector detector = new FailedConnectionDetector(50);
        detector.onConnectFailed(new RuntimeException());

        FailedNodeDetector copy = detector.copy();
        assertThat(copy).isInstanceOf(FailedConnectionDetector.class);

        Thread.sleep(60);
        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(copy.isNodeFailed()).isFalse();
    }

    @Test
    public void testFailedCommandsDetectorCopyKeepsConfigAndResetsState() throws InterruptedException {
        // FailedCommandsDetector tracks failures by millisecond timestamp, so the
        // failures are spaced out to make sure they count as distinct commands.
        FailedCommandsDetector detector = new FailedCommandsDetector(60000, 2);
        detector.onCommandFailed(new RuntimeException());
        Thread.sleep(2);
        detector.onCommandFailed(new RuntimeException());
        assertThat(detector.isNodeFailed()).isTrue();

        FailedNodeDetector copy = detector.copy();
        assertThat(copy).isInstanceOf(FailedCommandsDetector.class);

        // configured limit is preserved, but the accumulated failures are not
        assertThat(copy.isNodeFailed()).isFalse();
        copy.onCommandFailed(new RuntimeException());
        Thread.sleep(2);
        copy.onCommandFailed(new RuntimeException());
        assertThat(copy.isNodeFailed()).isTrue();
    }

    @Test
    public void testFailedCommandsTimeoutDetectorCopyPreservesType() {
        FailedCommandsTimeoutDetector detector = new FailedCommandsTimeoutDetector(60000, 1);

        FailedNodeDetector copy = detector.copy();
        assertThat(copy).isInstanceOf(FailedCommandsTimeoutDetector.class);

        // only timeout errors count towards failure for this detector
        copy.onCommandFailed(new RuntimeException());
        assertThat(copy.isNodeFailed()).isFalse();
        copy.onCommandFailed(new RedisTimeoutException("timeout"));
        assertThat(copy.isNodeFailed()).isTrue();
    }

    @Test
    public void testDefaultDetectorCopyReturnsSameInstance() {
        FailedNodeDetector custom = new FailedNodeDetector() {
            @Override
            public void onConnectSuccessful() {
            }
            @Override
            public void onConnectFailed() {
            }
            @Override
            public void onPingSuccessful() {
            }
            @Override
            public void onPingFailed() {
            }
            @Override
            public void onCommandSuccessful() {
            }
            @Override
            public void onCommandFailed(Throwable cause) {
            }
            @Override
            public boolean isNodeFailed() {
                return false;
            }
        };

        assertThat(custom.copy()).isSameAs(custom);
    }
}
