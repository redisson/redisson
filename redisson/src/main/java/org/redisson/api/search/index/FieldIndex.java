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
 * Field index for {@link org.redisson.api.RSearch#createIndex(String, IndexOptions, FieldIndex...)} method
 *
 * @author Nikita Koksharov
 *
 */
public interface FieldIndex {

    /**
     * Returns numeric field index
     *
     * @param fieldName field name
     * @return options object
     */
    static NumericIndex numeric(String fieldName) {
        return new NumericIndexParams(fieldName);
    }

    /**
     * Returns tag field index
     *
     * @param fieldName field name
     * @return options object
     */
    static TagIndex tag(String fieldName) {
        return new TagIndexParams(fieldName);
    }

    /**
     * Returns text field index
     *
     * @param fieldName field name
     * @return options object
     */
    static TextIndex text(String fieldName) {
        return new TextIndexParams(fieldName);
    }

    /**
     * Returns vector field index which uses FLAT indexing method
     *
     * @param fieldName field name
     * @return options object
     */
    static FlatVectorIndex flatVector(String fieldName) {
        return new FlatVectorIndexParams(fieldName);
    }

    /**
     * Returns vector field index which uses HNSW indexing method
     *
     * @param fieldName field name
     * @return options object
     */
    static HNSWVectorIndex hnswVector(String fieldName) {
        return new HNSWVectorIndexParams(fieldName);
    }

    /**
     * Returns vector field index which uses SVS-VAMANA indexing method
     *
     * @param fieldName field name
     * @return options object
     */
    static SVSVamanaVectorIndex svsVamanaVector(String fieldName) {
        return new SVSVamanaVectorIndexParams(fieldName);
    }

    /**
     * Returns geo field index
     *
     * @param fieldName field name
     * @return options object
     */
    static GeoIndex geo(String fieldName) {
        return new GeoIndexParams(fieldName);
    }

    /**
     * Returns geoshape field index
     *
     * @param fieldName field name
     * @return options object
     */
    static GeoShapeIndex geoShape(String fieldName) {
        return new GeoShapeIndexParams(fieldName);
    }
}
