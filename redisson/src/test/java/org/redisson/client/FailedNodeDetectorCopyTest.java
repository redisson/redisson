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
package org.redisson.client;

import org.junit.jupiter.api.Test;
import org.redisson.api.NodeType;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class FailedNodeDetectorCopyTest {

    @Test
    void failedConnectionDetectorCopyHasIndependentState() throws Exception {
        FailedConnectionDetector detector = new FailedConnectionDetector(1);
        FailedNodeDetector copy = detector.copy();

        detector.onConnectFailed(new RedisConnectionException("test"));
        Thread.sleep(5);

        assertThat(copy).isInstanceOf(FailedConnectionDetector.class).isNotSameAs(detector);
        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(copy.isNodeFailed()).isFalse();
    }

    @Test
    void failedCommandsDetectorCopyHasIndependentState() throws Exception {
        FailedCommandsDetector detector = new FailedCommandsDetector(10000, 2);
        FailedNodeDetector copy = detector.copy();

        failCommand(detector, new RedisConnectionException("test"));
        failCommand(detector, new RedisConnectionException("test"));

        assertThat(copy).isInstanceOf(FailedCommandsDetector.class).isNotSameAs(detector);
        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(copy.isNodeFailed()).isFalse();
    }

    @Test
    void failedCommandsTimeoutDetectorCopyHasIndependentStateAndType() throws Exception {
        FailedCommandsTimeoutDetector detector = new FailedCommandsTimeoutDetector(10000, 2);
        FailedNodeDetector copy = detector.copy();

        failCommand(detector, new RedisConnectionException("test"));
        failCommand(detector, new RedisTimeoutException("test"));
        failCommand(detector, new RedisTimeoutException("test"));

        assertThat(copy).isInstanceOf(FailedCommandsTimeoutDetector.class).isNotSameAs(detector);
        assertThat(detector.isNodeFailed()).isTrue();
        assertThat(copy.isNodeFailed()).isFalse();
    }

    @Test
    void redisClientConfigCopyCopiesDetectorState() {
        RedisClientConfig config = new RedisClientConfig()
                .setNodeType(NodeType.MASTER)
                .setFailedNodeDetector(new FailedCommandsTimeoutDetector(10000, 2));
        RedisClientConfig copy = new RedisClientConfig(config);

        assertThat(copy.getNodeType()).isEqualTo(NodeType.MASTER);
        assertThat(copy.getFailedNodeDetector()).isInstanceOf(FailedCommandsTimeoutDetector.class);
        assertThat(copy.getFailedNodeDetector()).isNotSameAs(config.getFailedNodeDetector());
    }

    @Test
    void nodeFailureReporterClassifiesCommonRedisFailures() {
        assertThat(NodeFailureReporter.classify(new RedisException("ERR max number of clients reached")))
                .isEqualTo(NodeFailureCategory.MAX_CLIENTS);
        assertThat(NodeFailureReporter.classify(new RedisResponseTimeoutException("timeout")))
                .isEqualTo(NodeFailureCategory.RESPONSE_TIMEOUT);
        assertThat(NodeFailureReporter.classify(new RedisWrongPasswordException("wrong password")))
                .isEqualTo(NodeFailureCategory.WRONG_PASSWORD);
        assertThat(NodeFailureReporter.classify(new RedisConnectionException("connect failed", new ConnectException("refused"))))
                .isEqualTo(NodeFailureCategory.CONNECTION_REFUSED);
    }

    private void failCommand(FailedNodeDetector detector, Throwable cause) throws Exception {
        detector.onCommandFailed(cause);
        Thread.sleep(2);
    }
}
