package org.redisson.api;

import java.util.List;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorInfo;
import org.redisson.api.vector.VectorSimilarArgs;
import org.redisson.client.protocol.ScoredEntry;

/**
 * Async interface for Redis Vector Set
 *
 * @author Nikita Koksharov
 *
 */
public interface RVectorSetAsync extends RExpirableAsync {
    /**
     * Adds an element to the vector set asynchronously
     *
     * @param args vector add arguments
     * @return <code>true</code> if element was added and <code>false</code> if updated
     */
    RFuture<Boolean> addAsync(VectorAddArgs args);

    /**
     * Returns the number of elements in the vector set asynchronously
     *
     * @return cardinality of the vector set
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns the dimension of vectors in the vector set asynchronously
     *
     * @return dimension count
     */
    RFuture<Integer> dimensionsAsync();

    /**
     * Retrieves the vector associated with an element asynchronously
     *
     * @param element element name
     * @return list of vector coordinates
     */
    RFuture<List<Double>> getVectorAsync(String element);

    /**
     * Retrieves the raw vector associated with an element asynchronously
     *
     * @param element element name
     * @return list of raw vector values
     */
    RFuture<List<Object>> getRawVectorAsync(String element);

    /**
     * Retrieves attributes of a vector set element asynchronously
     *
     * @param element element name
     * @param clazz class type for deserialization
     * @return deserialized attributes object
     */
    <T> RFuture<T> getAttributesAsync(String element, Class<T> clazz);

    /**
     * Returns metadata and internal details about the vector set asynchronously
     *
     * @return vector set information
     */
    RFuture<VectorInfo> getInfoAsync();

    /**
     * Retrieves the neighbors of a specified element asynchronously
     *
     * @param element element name
     * @return list of neighbor element names
     */
    RFuture<List<String>> getNeighborsAsync(String element);

    /**
     * Retrieves the neighbors of a specified element with scores asynchronously
     *
     * @param element element name
     * @return list of neighbor elements with scores
     */
    RFuture<List<ScoredEntry<String>>> getNeighborEntriesAsync(String element);

    /**
     * Returns a random element from the vector set asynchronously
     *
     * @return random element name
     */
    RFuture<String> randomAsync();

    /**
     * Returns multiple random elements from the vector set asynchronously
     *
     * @param count number of elements to return
     * @return list of random element names
     */
    RFuture<List<String>> randomAsync(int count);

    /**
     * Removes an element from the vector set asynchronously
     *
     * @param element element name to remove
     * @return true if element was removed, false otherwise
     */
    RFuture<Boolean> removeAsync(String element);

    /**
     * Sets attributes for a vector set element asynchronously
     *
     * @param element element name
     * @param attributes attributes object to serialize
     * @return true if attributes were set, false otherwise
     */
    RFuture<Boolean> setAttributesAsync(String element, Object attributes);

    /**
     * Retrieves elements similar to a given vector or element asynchronously
     *
     * @param args vector similarity arguments
     * @return list of similar element names
     */
    RFuture<List<String>> getSimilarAsync(VectorSimilarArgs args);

    /**
     * Retrieves elements similar to a given vector or element with scores asynchronously
     *
     * @param args vector similarity arguments
     * @return list of similar elements with scores
     */
    RFuture<List<ScoredEntry<String>>> getSimilarEntriesAsync(VectorSimilarArgs args);
}
