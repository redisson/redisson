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
    void addressAwareMethodsReceiveSuppliedAddress() {
        AddressAwareDetector detector = new AddressAwareDetector();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);
        RedisConnectionException cause = new RedisConnectionException("test");

        detector.onConnectSuccessful(address);
        detector.onConnectFailed(cause, address);
        detector.onPingSuccessful(address);
        detector.onPingFailed(cause, address);
        detector.onCommandSuccessful(address);
        detector.onCommandFailed(cause, address);

        assertThat(detector.addresses).containsOnly(address);
        assertThat(detector.causes).containsOnly(cause);
        assertThat(detector.isNodeFailed(address)).isTrue();
        assertThat(detector.failedCheckAddress).isEqualTo(address);
    }

    @Test
    void addressAwareMethodsDelegateToLegacyMethodsByDefault() {
        TrackingDetector detector = new TrackingDetector();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 6379);
        RedisConnectionException cause = new RedisConnectionException("test");

        detector.onConnectSuccessful(address);
        detector.onConnectFailed(cause, address);
        detector.onPingSuccessful(address);
        detector.onPingFailed(cause, address);
        detector.onCommandSuccessful(address);
        detector.onCommandFailed(cause, address);

        assertThat(detector.connectSuccessfulCalls).isEqualTo(1);
        assertThat(detector.connectFailedCalls).isEqualTo(1);
        assertThat(detector.pingSuccessfulCalls).isEqualTo(1);
        assertThat(detector.pingFailedCalls).isEqualTo(1);
        assertThat(detector.commandSuccessfulCalls).isEqualTo(1);
        assertThat(detector.commandFailedCalls).isEqualTo(1);
        assertThat(detector.lastCause).isEqualTo(cause);
        assertThat(detector.isNodeFailed(address)).isTrue();
        assertThat(detector.nodeFailedCalls).isEqualTo(1);
    }

    private void failCommand(FailedNodeDetector detector, Throwable cause) throws Exception {
        detector.onCommandFailed(cause);
        Thread.sleep(2);
    }

    private static final class TrackingDetector implements FailedNodeDetector {
        private int connectSuccessfulCalls;
        private int connectFailedCalls;
        private int pingSuccessfulCalls;
        private int pingFailedCalls;
        private int commandSuccessfulCalls;
        private int commandFailedCalls;
        private int nodeFailedCalls;
        private Throwable lastCause;

        @Override
        public void onConnectSuccessful() {
            connectSuccessfulCalls++;
        }

        @Override
        public void onConnectFailed() {
            connectFailedCalls++;
        }

        @Override
        public void onPingSuccessful() {
            pingSuccessfulCalls++;
        }

        @Override
        public void onPingFailed() {
            pingFailedCalls++;
        }

        @Override
        public void onCommandSuccessful() {
            commandSuccessfulCalls++;
        }

        @Override
        public void onCommandFailed(Throwable cause) {
            commandFailedCalls++;
            lastCause = cause;
        }

        @Override
        public boolean isNodeFailed() {
            nodeFailedCalls++;
            return true;
        }

        @Override
        public FailedNodeDetector copy() {
            return new TrackingDetector();
        }
    }

    private static final class AddressAwareDetector implements FailedNodeDetector {
        private final InetSocketAddress[] addresses = new InetSocketAddress[6];
        private final Throwable[] causes = new Throwable[3];
        private InetSocketAddress failedCheckAddress;

        @Override
        public void onConnectSuccessful() {
        }

        @Override
        public void onConnectSuccessful(InetSocketAddress address) {
            addresses[0] = address;
        }

        @Override
        public void onConnectFailed() {
        }

        @Override
        public void onConnectFailed(Throwable cause, InetSocketAddress address) {
            causes[0] = cause;
            addresses[1] = address;
        }

        @Override
        public void onPingSuccessful() {
        }

        @Override
        public void onPingSuccessful(InetSocketAddress address) {
            addresses[2] = address;
        }

        @Override
        public void onPingFailed() {
        }

        @Override
        public void onPingFailed(Throwable cause, InetSocketAddress address) {
            causes[1] = cause;
            addresses[3] = address;
        }

        @Override
        public void onCommandSuccessful() {
        }

        @Override
        public void onCommandSuccessful(InetSocketAddress address) {
            addresses[4] = address;
        }

        @Override
        public void onCommandFailed(Throwable cause) {
        }

        @Override
        public void onCommandFailed(Throwable cause, InetSocketAddress address) {
            causes[2] = cause;
            addresses[5] = address;
        }

        @Override
        public boolean isNodeFailed() {
            return false;
        }

        @Override
        public boolean isNodeFailed(InetSocketAddress address) {
            failedCheckAddress = address;
            return true;
        }
    }
}
