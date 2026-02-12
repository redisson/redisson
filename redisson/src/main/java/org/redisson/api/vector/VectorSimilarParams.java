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
    private Double epsilon;
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
    public VectorSimilarArgs epsilon(double value) {
        this.epsilon = value;
        return this;
    }

    @Override
    public VectorSimilarArgs explorationFactor(int effort) {
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

    public Double getEpsilon() {
        return epsilon;
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
