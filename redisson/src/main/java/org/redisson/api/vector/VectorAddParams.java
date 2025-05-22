package org.redisson.api.vector;

/**
 * Vector add arguments implementation
 *
 * @author Nikita Koksharov
 *
 */
public final class VectorAddParams implements VectorAddArgs, VectorAddArgs.ElementStep {
    private final String element;
    private byte[] vectorBytes;
    private Double[] vectorDoubles;
    private Integer reduce;
    private boolean useCheckAndSet;
    private QuantizationType quantizationType;
    private Integer effort;
    private Object attributes;
    private Integer maxConnections;

    VectorAddParams(String element) {
        this.element = element;
    }

    @Override
    public VectorAddArgs vector(byte[] vector) {
        this.vectorBytes = vector;
        return this;
    }

    @Override
    public VectorAddArgs vector(Double... vector) {
        this.vectorDoubles = vector;
        return this;
    }

    @Override
    public VectorAddArgs reduce(int reduce) {
        this.reduce = reduce;
        return this;
    }

    @Override
    public VectorAddArgs useCheckAndSet() {
        this.useCheckAndSet = true;
        return this;
    }

    @Override
    public VectorAddArgs quantization(QuantizationType type) {
        this.quantizationType = type;
        return this;
    }

    @Override
    public VectorAddArgs effort(int effort) {
        this.effort = effort;
        return this;
    }

    @Override
    public VectorAddArgs attributes(Object attributes) {
        this.attributes = attributes;
        return this;
    }

    @Override
    public VectorAddArgs maxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public String getElement() {
        return element;
    }

    public byte[] getVectorBytes() {
        return vectorBytes;
    }

    public Double[] getVectorDoubles() {
        return vectorDoubles;
    }

    public Integer getReduce() {
        return reduce;
    }

    public boolean isUseCheckAndSet() {
        return useCheckAndSet;
    }

    public QuantizationType getQuantizationType() {
        return quantizationType;
    }

    public Integer getEffort() {
        return effort;
    }

    public Object getAttributes() {
        return attributes;
    }

    public Integer getMaxConnections() {
        return maxConnections;
    }

}
