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
 * Optional options object for vector field index which uses SVS-VAMANA indexing method.
 *
 * @author seakider
 */
public interface SVSVamanaVectorOptionalArgs extends FieldIndex {

    enum CompressionAlgorithm {LVQ8, LVQ4, LVQ4x4, LVQ4x8, LeanVec4x8, LeanVec8x8}

    /**
     * Defines compression algorithm
     *
     * @param algorithm compression algorithm
     * @return vector options
     */
    SVSVamanaVectorOptionalArgs compression(CompressionAlgorithm algorithm);

    /**
     * Defines number of the search window size to during graph construction
     *
     * @param value number of search window size
     * @return vector options
     */
    SVSVamanaVectorOptionalArgs constructionWindowSize(int value);

    /**
     * Defines the maximum number of edges per node
     *
     * @param value the maximum number of edges
     * @return vector options
     */
    SVSVamanaVectorOptionalArgs graphMaxDegree(int value);

    /**
     * Defines the size of the search window
     *
     * @param value size of the search window
     * @return vector options
     */
    SVSVamanaVectorOptionalArgs searchWindowSize(int value);

    /**
     * Defines relative factor that sets the boundaries in which a range query may search for candidates
     *
     * @param value relative factor
     * @return vector options
     */
    SVSVamanaVectorOptionalArgs epsilon(double value);

    /**
     * Defines number of vectors needed to learn compression parameters.
     *
     * @param value number of vectors
     * @return
     */
    SVSVamanaVectorOptionalArgs trainingThreshold(int value);

    /**
     * Defines the dimension used when using LeanVec4x8 or LeanVec8x8
     *
     * @param value the dimension
     * @return
     */
    SVSVamanaVectorOptionalArgs leanVecDim(int value);

}
