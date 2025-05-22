package org.redisson.api.vector;

/**
 * Vector similarity arguments implementation
 *
 * @author Nikita Koksharov
 *
 */
public final class VectorSimilarParams implements VectorSimilarArgs {
    private final String element;
    private final byte[] vectorBytes;
    private final Double[] vectorDoubles;
    private Integer count;
    private Integer effort;
    private String filter;
    private Integer filterEffort;
    private boolean useLinearScan;
    private boolean useMainThread;

    VectorSimilarParams(String element) {
        this.element = element;
        this.vectorBytes = null;
        this.vectorDoubles = null;
    }

    VectorSimilarParams(byte[] vector) {
        this.element = null;
        this.vectorBytes = vector;
        this.vectorDoubles = null;
    }

    VectorSimilarParams(Double... vector) {
        this.element = null;
        this.vectorBytes = null;
        this.vectorDoubles = vector;
    }

    @Override
    public VectorSimilarArgs count(int count) {
        this.count = count;
        return this;
    }

    @Override
    public VectorSimilarArgs effort(int effort) {
        this.effort = effort;
        return this;
    }

    @Override
    public VectorSimilarArgs filter(String filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public VectorSimilarArgs filterEffort(int filterEffort) {
        this.filterEffort = filterEffort;
        return this;
    }

    @Override
    public VectorSimilarArgs useLinearScan() {
        this.useLinearScan = true;
        return this;
    }

    @Override
    public VectorSimilarArgs useMainThread() {
        this.useMainThread = true;
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

    public Integer getCount() {
        return count;
    }

    public Integer getEffort() {
        return effort;
    }

    public String getFilter() {
        return filter;
    }

    public Integer getFilterEffort() {
        return filterEffort;
    }

    public boolean isUseLinearScan() {
        return useLinearScan;
    }

    public boolean isUseMainThread() {
        return useMainThread;
    }

}
