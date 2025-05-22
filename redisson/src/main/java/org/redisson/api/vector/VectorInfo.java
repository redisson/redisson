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
