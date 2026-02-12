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
 * Arguments object for RVectorSet.getSimilar() method
 *
 * @author Nikita Koksharov
 *
 */
public interface VectorSimilarArgs {

    /**
     * Defines element name.
     *
     * @param element element name
     * @return arguments object
     */
    static VectorSimilarArgs element(String element) {
        return new VectorSimilarParams(element);
    }

    /**
     * Defines vector as byte array (32-bit floating point blob of values).
     *
     * @param vector as byte array
     * @return arguments object
     */
    static VectorSimilarArgs vector(byte[] vector) {
        return new VectorSimilarParams(vector);
    }

    /**
     * Defines vector as array of floating point numbers.
     *
     * @param vector vector as array of doubles
     * @return arguments object
     */
    static VectorSimilarArgs vector(Double... vector) {
        return new VectorSimilarParams(vector);
    }

    /**
     * Defines the count parameter.
     *
     * @param count count value
     * @return arguments object
     */
    VectorSimilarArgs count(int count);

    /**
     * Defines the distance that is no further than the specified delta.
     *
     * @param value a floating point number between 0 and 1.
     * @return arguments object
     */
    VectorSimilarArgs epsilon(double value);
    /**
     * Defines the exploration factor (EF).
     *
     * @param value exploration factor value
     * @return arguments object
     */
    VectorSimilarArgs explorationFactor(int value);

    /**
     * Defines the filter expression to restrict matching elements.
     *
     * @param expression expression value
     * @return arguments object
     */
    VectorSimilarArgs filter(String expression);

    /**
     * Defines the limit of filtering attempts for the filter expression.
     *
     * @param filterEffort - filter effort value
     * @return arguments object
     */
    VectorSimilarArgs filterEffort(int filterEffort);

    /**
     * Defines whether a linear scan is used to obtain exact results.
     *
     * @return arguments object
     */
    VectorSimilarArgs useLinearScan();

    /**
     * Defines whether the search is executed in the main thread or a background thread.
     *
     * @return arguments object
     */
    VectorSimilarArgs useMainThread();
}
