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
package org.redisson.api.pubsub;

/**
 * A push-based consumer that receives messages automatically via registered listeners.
 * <p>
 * Push consumers provide event-driven message processing, where messages are delivered
 * to the application as soon as they become available.
 * <p>
 * Messages delivered to listeners must be explicitly acknowledged using
 * {@link Acknowledgment#acknowledge(MessageAckArgs)} or
 * {@link Acknowledgment#negativeAcknowledge(MessageNegativeAckArgs)}.
 * Unacknowledged messages will be automatically redelivered after the visibility timeout expires.
 *
 * @param <V> the type of message values
 *
 * @author Nikita Koksharov
 *
 */
public interface PushConsumer<V> extends Consumer {

    /**
     * Registers a listener to receive messages from this consumer.
     * <p>
     * Once registered, the listener will be invoked automatically whenever
     * new messages are available in the subscription. The listener is responsible
     * for processing messages and sending acknowledgments.
     * <p>
     * Only one listener can be registered per consumer. To use a different listener,
     * create a new consumer instance.
     *
     * @param listenerArgs the listener configuration including the message handler
     * @throws IllegalStateException if a listener has already been registered
     */
    void registerListener(MessageListenerArgs<V> listenerArgs);

}
