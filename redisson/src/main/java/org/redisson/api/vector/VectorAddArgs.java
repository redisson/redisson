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
