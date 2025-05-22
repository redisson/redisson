package org.redisson.api.vector;

/**
 * Vector similarity arguments
 *
 * @author Nikita Koksharov
 *
 */
public interface VectorSimilarArgs {
    /**
     * Creates arguments for finding similar vectors to the specified element
     *
     * @param element - element name
     * @return vector similarity arguments
     */
    static VectorSimilarArgs element(String element) {
        return new VectorSimilarParams(element);
    }

    /**
     * Creates arguments for finding similar vectors to the specified vector
     *
     * @param vector - vector as byte array
     * @return vector similarity arguments
     */
    static VectorSimilarArgs vector(byte[] vector) {
        return new VectorSimilarParams(vector);
    }

    /**
     * Creates arguments for finding similar vectors to the specified vector
     *
     * @param vector - vector as array of doubles
     * @return vector similarity arguments
     */
    static VectorSimilarArgs vector(Double... vector) {
        return new VectorSimilarParams(vector);
    }

    /**
     * Sets the count parameter
     *
     * @param count - count value
     * @return vector similarity arguments
     */
    VectorSimilarArgs count(int count);

    /**
     * Sets the effort parameter
     *
     * @param effort - effort value
     * @return vector similarity arguments
     */
    VectorSimilarArgs effort(int effort);

    /**
     * Sets the filter expression
     *
     * @param filter - filter expression
     * @return vector similarity arguments
     */
    VectorSimilarArgs filter(String filter);

    /**
     * Sets the filter effort parameter
     *
     * @param filterEffort - filter effort value
     * @return vector similarity arguments
     */
    VectorSimilarArgs filterEffort(int filterEffort);

    /**
     * Enables linear scan for exact results
     *
     * @return vector similarity arguments
     */
    VectorSimilarArgs useLinearScan();

    /**
     * Forces execution on the main thread
     *
     * @return vector similarity arguments
     */
    VectorSimilarArgs useMainThread();
}
