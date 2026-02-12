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
package org.redisson.api.search.index;

/**
 *
 * @author seakider
 *
 */
public class SVSVamanaVectorIndexParams implements SVSVamanaVectorIndex,
                                            VectorDimParam<SVSVamanaVectorOptionalArgs>,
                                            VectorDistParam<SVSVamanaVectorOptionalArgs>,
                                            SVSVamanaVectorOptionalArgs {
    private final String fieldName;
    private Type type;
    private int dim;
    private DistanceMetric distanceMetric;
    private int count;
    private String as;

    private CompressionAlgorithm compressionAlgorithm;
    private Integer constructionWindowSize;
    private Integer graphMaxDegree;
    private Integer searchWindowSize;
    private Double epsilon;
    private Integer trainingThreshold;
    private Integer leanVecDim;

    SVSVamanaVectorIndexParams(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public SVSVamanaVectorIndexParams as(String as) {
        this.as = as;
        return this;
    }

    @Override
    public VectorDistParam<SVSVamanaVectorOptionalArgs> dim(int value) {
        count++;
        this.dim = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs distance(DistanceMetric metric) {
        count++;
        this.distanceMetric = metric;
        return this;
    }

    @Override
    public VectorDimParam<SVSVamanaVectorOptionalArgs> type(Type type) {
        count++;
        this.type = type;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs compression(CompressionAlgorithm algorithm) {
        count++;
        this.compressionAlgorithm = algorithm;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs constructionWindowSize(int value) {
        count++;
        this.constructionWindowSize = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs graphMaxDegree(int value) {
        count++;
        this.graphMaxDegree = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs searchWindowSize(int value) {
        count++;
        this.searchWindowSize = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs epsilon(double value) {
        count++;
        this.epsilon = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs trainingThreshold(int value) {
        count++;
        this.trainingThreshold = value;
        return this;
    }

    @Override
    public SVSVamanaVectorOptionalArgs leanVecDim(int value) {
        count++;
        this.leanVecDim = value;
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

    public int getCount() {
        return count;
    }

    public String getAs() {
        return as;
    }

    public CompressionAlgorithm getCompressionAlgorithm() {
        return compressionAlgorithm;
    }

    public Integer getConstructionWindowSize() {
        return constructionWindowSize;
    }

    public Integer getGraphMaxDegree() {
        return graphMaxDegree;
    }

    public Integer getSearchWindowSize() {
        return searchWindowSize;
    }

    public Double getEpsilon() {
        return epsilon;
    }

    public Integer getTrainingThreshold() {
        return trainingThreshold;
    }

    public Integer getLeanVecDim() {
        return leanVecDim;
    }
}
