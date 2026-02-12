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
package org.redisson.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Message object.
 *
 * @author Nikita Koksharov
 * @param <V> type
 *
 */
public final class Message<V> {

    String id;
    V payload;
    Map<String, Object> headers;

    public Message(String id, V payload, Map<String, Object> headers) {
        this.id = id;
        this.payload = payload;
        this.headers = Collections.unmodifiableMap(headers);
    }

    public <T> Map<String, T> getHeaders() {
        return (Map<String, T>) headers;
    }

    public String getId() {
        return id;
    }

    public V getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Message<?> message = (Message<?>) o;
        return Objects.equals(id, message.id) && Objects.equals(payload, message.payload) && Objects.equals(headers, message.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, payload, headers);
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", payload=" + payload +
                ", headers=" + headers +
                '}';
    }
}
