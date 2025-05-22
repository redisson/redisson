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
package org.redisson.api.vector;

import java.io.Serializable;

/**
 * Vector information object
 *
 * @author Nikita Koksharov
 *
 */
public final class VectorInfo implements Serializable {

    private long attributesCount;
    private long dimensions;
    private long size;
    private QuantizationType quantizationType;
    private long maxConnections;

    public long getAttributesCount() {
        return attributesCount;
    }

    public void setAttributesCount(long attributesCount) {
        this.attributesCount = attributesCount;
    }

    public long getDimensions() {
        return dimensions;
    }

    public void setDimensions(long dimensions) {
        this.dimensions = dimensions;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public QuantizationType getQuantizationType() {
        return quantizationType;
    }

    public void setQuantizationType(QuantizationType quantizationType) {
        this.quantizationType = quantizationType;
    }

    public long getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(long maxConnections) {
        this.maxConnections = maxConnections;
    }
}
