/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Result object for autoClaim request.
 *
 * @see RStream#autoClaim(String, String, long, TimeUnit, StreamMessageId, int)
 * @see RStreamAsync#autoClaimAsync(String, String, long, TimeUnit, StreamMessageId, int)
 * 
 * @author Nikita Koksharov
 *
 */
public class AutoClaimResult<K, V> implements Serializable {

    private static final long serialVersionUID = -5525031552305408248L;

    private StreamMessageId nextId;
    private Map<StreamMessageId, Map<K, V>> messages;

    public AutoClaimResult() {
    }

    public AutoClaimResult(StreamMessageId nextId, Map<StreamMessageId, Map<K, V>> messages) {
        super();
        this.nextId = nextId;
        this.messages = messages;
    }

    public StreamMessageId getNextId() {
        return nextId;
    }

    public Map<StreamMessageId, Map<K, V>> getMessages() {
        return messages;
    }
}
