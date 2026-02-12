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
package org.redisson.api.search.query;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class Document {

    private final String id;
    private Map<String, Object> attributes;
    private byte[] payload;
    private Double score;

    public Document(String id) {
        this.id = id;
    }

    public Document(String id, Map<String, Object> attributes) {
        this.id = id;
        this.attributes = attributes;
    }

    /**
     * Returns document id
     *
     * @return document id
     */
    public String getId() {
        return id;
    }

    /**
     * Returns document attributes
     *
     * @return document attributes
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Returns document payload
     *
     * @return document payload
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Returns document score
     *
     * @return document score
     */
    public Double getScore() {
        return score;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id) && Objects.equals(attributes, document.attributes) && Arrays.equals(payload, document.payload) && Objects.equals(score, document.score);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, attributes, score);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }
}
