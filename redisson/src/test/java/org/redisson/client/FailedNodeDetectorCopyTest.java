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
import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;

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
        TrackingDetector detector = new TrackingDetector();
        RedisClientConfig config = new RedisClientConfig()
                .setFailedNodeDetector(detector);

        RedisClientConfig copy = new RedisClientConfig(config);

        assertThat(copy.getFailedNodeDetector()).isInstanceOf(TrackingDetector.class);
        assertThat(copy.getFailedNodeDetector()).isNotSameAs(detector);
    }

    @Test
    void redisClientConfigCopyAssignsResolvedAddress() {
        TrackingDetector detector = new TrackingDetector();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);
        RedisURI uri = new RedisURI("redis://localhost:6379");
        RedisClientConfig config = new RedisClientConfig()
                .setAddress(address, uri)
                .setFailedNodeDetector(detector);

        RedisClientConfig copy = new RedisClientConfig(config);

        assertThat(((TrackingDetector) copy.getFailedNodeDetector()).address).isEqualTo(address);
    }

    @Test
    void redisClientConfigCopyAssignsUnresolvedUriAddress() {
        TrackingDetector detector = new TrackingDetector();
        RedisClientConfig config = new RedisClientConfig()
                .setAddress("redis://redis.example.com:6379")
                .setFailedNodeDetector(detector);

        RedisClientConfig copy = new RedisClientConfig(config);
        InetSocketAddress address = ((TrackingDetector) copy.getFailedNodeDetector()).address;

        assertThat(address.getHostString()).isEqualTo("redis.example.com");
        assertThat(address.getPort()).isEqualTo(6379);
        assertThat(address.isUnresolved()).isTrue();
    }

    private void failCommand(FailedNodeDetector detector, Throwable cause) throws Exception {
        detector.onCommandFailed(cause);
        Thread.sleep(2);
    }

    private static final class TrackingDetector implements FailedNodeDetector {
        private InetSocketAddress address;

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

        @Override
        public void setNodeAddress(InetSocketAddress address) {
            this.address = address;
        }

        @Override
        public FailedNodeDetector copy() {
            return new TrackingDetector();
        }
    }
}
