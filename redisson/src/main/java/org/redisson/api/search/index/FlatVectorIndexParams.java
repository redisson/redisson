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
package org.redisson.api.search.index;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class FlatVectorIndexParams implements FlatVectorIndex,
                                              VectorDimParam<FlatVectorOptionalArgs>,
                                              VectorDistParam<FlatVectorOptionalArgs>,
                                              FlatVectorOptionalArgs {

    private final String fieldName;
    private VectorTypeParam.Type type;
    private int dim;
    private VectorDistParam.DistanceMetric distanceMetric;
    private Integer initialCapacity;
    private Integer blockSize;
    private int count;
    private String as;

    FlatVectorIndexParams(String name) {
        this.fieldName = name;
    }

    @Override
    public FlatVectorIndexParams as(String as) {
        this.as = as;
        return this;
    }

    @Override
    public VectorDimParam<FlatVectorOptionalArgs> type(Type type) {
        count++;
        this.type = type;
        return this;
    }

    @Override
    public VectorDistParam<FlatVectorOptionalArgs> dim(int value) {
        count++;
        this.dim = value;
        return this;
    }

    @Override
    public FlatVectorOptionalArgs distance(DistanceMetric metric) {
        count++;
        this.distanceMetric = metric;
        return this;
    }

    @Override
    public FlatVectorOptionalArgs initialCapacity(int value) {
        count++;
        this.initialCapacity = value;
        return this;
    }

    @Override
    public FlatVectorOptionalArgs blockSize(int value) {
        count++;
        this.blockSize = value;
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

    public Integer getInitialCapacity() {
        return initialCapacity;
    }

    public Integer getBlockSize() {
        return blockSize;
    }

    public int getCount() {
        return count;
    }

    public String getAs() {
        return as;
    }
}
