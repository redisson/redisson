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

import org.redisson.api.BaseSyncParams;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MessageAckParams extends BaseSyncParams<MessageAckArgs> implements MessageAckArgs {

    private final Map<String, Integer> deliveriesById;

    public MessageAckParams(String[] ids) {
        this.deliveriesById = Arrays.stream(ids).collect(Collectors.toMap(Function.identity(), s -> 0));
    }

    public MessageAckParams(Map<String, Integer> value) {
        this.deliveriesById = value;
    }

    public Map<String, Integer> getDeliveriesById() {
        return deliveriesById;
    }
}
