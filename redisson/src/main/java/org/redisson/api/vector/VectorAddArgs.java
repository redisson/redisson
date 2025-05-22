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
package org.redisson.api.vector;

/**
 * Vector add arguments
 *
 * @author Nikita Koksharov
 *
 */
public interface VectorAddArgs {
    /**
     * Creates a new element with the specified name
     *
     * @param element element name
     * @return element vector builder
     */
    static ElementStep element(String element) {
        return new VectorAddParams(element);
    }

    /**
     * Element step interface for building vector add arguments
     */
    interface ElementStep {
        /**
         * Sets the vector as byte array
         *
         * @param vector vector as byte array
         * @return vector add arguments
         */
        VectorAddArgs vector(byte[] vector);

        /**
         * Sets the vector as array of doubles
         *
         * @param vector vector as array of doubles
         * @return vector add arguments
         */
        VectorAddArgs vector(Double... vector);
    }

    /**
     * Sets the reduce parameter
     *
     * @param reduce reduce value
     * @return vector add arguments
     */
    VectorAddArgs reduce(int reduce);

    /**
     * Enables check-and-set operation
     *
     * @return vector add arguments
     */
    VectorAddArgs useCheckAndSet();

    /**
     * Sets the quantization type
     *
     * @param type quantization type
     * @return vector add arguments
     */
    VectorAddArgs quantization(QuantizationType type);

    /**
     * Sets the effort parameter
     *
     * @param effort effort value
     * @return vector add arguments
     */
    VectorAddArgs effort(int effort);

    /**
     * Sets the attributes object
     *
     * @param attributes attributes object to serialize
     * @return vector add arguments
     */
    VectorAddArgs attributes(Object attributes);

    /**
     * Sets the maximum connections parameter
     *
     * @param maxConnections maximum connections value
     * @return vector add arguments
     */
    VectorAddArgs maxConnections(int maxConnections);
}
