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
package org.redisson.api.search.index;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class HNSWVectorIndexParams implements HNSWVectorIndex,
                                            VectorDimParam<HNSWVectorOptionalArgs>,
                                            VectorDistParam<HNSWVectorOptionalArgs>,
        HNSWVectorOptionalArgs {

    private final String fieldName;
    private Type type;
    private int dim;
    private DistanceMetric distanceMetric;
    private Integer initialCap;
    private Integer m;
    private Integer efConstruction;
    private Integer efRuntime;
    private Double epsilon;

    private int count;

    HNSWVectorIndexParams(String name) {
        this.fieldName = name;
    }

    @Override
    public VectorDimParam<HNSWVectorOptionalArgs> type(Type type) {
        count++;
        this.type = type;
        return this;
    }

    @Override
    public VectorDistParam<HNSWVectorOptionalArgs> dim(int value) {
        count++;
        this.dim = value;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs distance(DistanceMetric metric) {
        count++;
        this.distanceMetric = metric;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs initialCapacity(int value) {
        count++;
        this.initialCap = value;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs m(int value) {
        count++;
        this.m = value;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs efConstruction(int value) {
        count++;
        this.efConstruction = value;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs efRuntime(int value) {
        count++;
        this.efRuntime = value;
        return this;
    }

    @Override
    public HNSWVectorOptionalArgs epsilon(double value) {
        count++;
        this.epsilon = value;
        return this;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Type getType() {
        return type;
    }

    public int getDim() {
        return dim;
    }

    public DistanceMetric getDistanceMetric() {
        return distanceMetric;
    }

    public Integer getInitialCap() {
        return initialCap;
    }

    public Integer getM() {
        return m;
    }

    public Integer getEfConstruction() {
        return efConstruction;
    }

    public Integer getEfRuntime() {
        return efRuntime;
    }

    public Double getEpsilon() {
        return epsilon;
    }

    public int getCount() {
        return count;
    }
}
