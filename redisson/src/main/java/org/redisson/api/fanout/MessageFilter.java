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
package org.redisson.api.fanout;

import java.io.Serializable;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Interface for filtering messages in a ReliableFanout object.
 * <p>
 * Implementing this interface allows selective message delivery to subscribers
 * based on custom logic. The filter evaluates messages and determines if they
 * should be delivered to specific subscribers.
 * <p>
 * As a serializable BiPredicate, instances of this interface can be:
 * - Transmitted across network boundaries
 * - Replicated among all ReliableFanout objects
 * - Applied on each node during message publishing process
 * <p>
 * When implemented, the test method should return true if the message should be
 * delivered, or false to filter it out.
 *
 * @author Nikita Koksharov
 *
 */
public interface MessageFilter<V> extends BiPredicate<V, Map<String, Object>>, Serializable {
}
