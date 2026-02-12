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
package org.redisson.api.search.query.hybrid;

/**
 * Implementation of VectorSimilarity configuration.
 *
 * @author Nikita Koksharov
 */
public final class VectorSimilarityParams implements VectorSimilarityRange, VectorSimilarityNearestNeighbors, VectorSimilarityBasic {

    public enum VectorSearchMode {
        KNN,
        RANGE
    }

    private final String field;
    private final String param;
    private VectorSearchMode mode;
    
    private Integer knnK;
    private Integer efRuntime;
    
    private Double rangeRadius;
    private Double rangeEpsilon;
    
    private String scoreAlias;
    private String filter;

    VectorSimilarityParams(String field, String param) {
        this.field = field;
        this.param = param;
        this.mode = null;
    }

    @Override
    public VectorSimilarityRange range(double radius) {
        this.mode = VectorSearchMode.RANGE;
        this.rangeRadius = radius;
        return this;
    }

    @Override
    public VectorSimilarityNearestNeighbors nearestNeighbors(int k) {
        this.mode = VectorSearchMode.KNN;
        this.knnK = k;
        return this;
    }

    @Override
    public VectorSimilarity epsilon(double epsilon) {
        this.rangeEpsilon = epsilon;
        return this;
    }

    @Override
    public VectorSimilarity efRuntime(int efRuntime) {
        this.efRuntime = efRuntime;
        return this;
    }

    @Override
    public VectorSimilarityBasic scoreAlias(String value) {
        this.scoreAlias = value;
        return this;
    }

    @Override
    public VectorSimilarityBasic filter(String value) {
        this.filter = value;
        return this;
    }

    public String getField() {
        return field;
    }

    public String getParam() {
        return param;
    }

    public VectorSearchMode getMode() {
        return mode;
    }

    public Integer getKnnK() {
        return knnK;
    }

    public Integer getEfRuntime() {
        return efRuntime;
    }

    public Double getRangeRadius() {
        return rangeRadius;
    }

    public Double getRangeEpsilon() {
        return rangeEpsilon;
    }

    public String getScoreAlias() {
        return scoreAlias;
    }

    public String getFilter() {
        return filter;
    }
}