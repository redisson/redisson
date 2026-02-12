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

import org.redisson.codec.JsonCodec;

/**
 * Arguments object for RVectorSet.add() method
 *
 * @author Nikita Koksharov
 *
 */
public interface VectorAddArgs {

    /**
     * Defines element name
     *
     * @param name element name
     * @return arguments object
     */
    static ElementStep element(String name) {
        return new VectorAddParams(name);
    }

    /**
     * Arguments object for vector data
     */
    interface ElementStep {

        /**
         * Defines vector as byte array (32-bit floating point blob of values)
         *
         * @param vector vector as byte array
         * @return arguments object
         */
        VectorAddArgs vector(byte[] vector);

        /**
         * Defines vector as array of floating point numbers
         *
         * @param vector vector as array of doubles
         * @return arguments object
         */
        VectorAddArgs vector(Double... vector);
    }

    /**
     * Sets the random projection value to reduce the dimensionality of the vector.
     *
     * @param reduce value to reduce the dimensionality
     * @return arguments object
     */
    VectorAddArgs reduce(int reduce);

    /**
     * Defines whether it is to use check-and-set style for the addition execution.
     *
     * @return arguments object
     */
    VectorAddArgs useCheckAndSet();

    /**
     * Defines the quantization type
     *
     * @param type quantization type
     * @return arguments object
     */
    VectorAddArgs quantization(QuantizationType type);

    /**
     * Defines the exploration factor (EF)
     *
     * @param value exploration factor value
     * @return arguments object
     */
    VectorAddArgs explorationFactor(int value);

    /**
     * Defines the attributes. Used in the form of a JavaScript object.
     *
     * @param attributes attributes object to serialize
     * @return arguments object
     */
    VectorAddArgs attributes(Object attributes, JsonCodec jsonCodec);

    /**
     * Defines the number of maximum connections which each node will have with other nodes.
     *
     * @param maxConnections number of maximum connections
     * @return arguments object
     */
    VectorAddArgs maxConnections(int maxConnections);
}
