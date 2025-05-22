package org.redisson.api;

import java.util.List;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorInfo;
import org.redisson.api.vector.VectorSimilarArgs;
import org.redisson.client.protocol.ScoredEntry;

/**
 * Redis Vector Set interface
 *
 * @author Nikita Koksharov
 *
 */
public interface RVectorSet extends RExpirable, RVectorSetAsync {
    /**
     * Adds an element to the vector set
     *
     * @param args vector add arguments
     * @return <code>true</code> if element was added and <code>false</code> if updated
     */
    boolean add(VectorAddArgs args);

    /**
     * Returns the number of elements in the vector set
     *
     * @return cardinality of the vector set
     */
    int size();

    /**
     * Returns the dimension of vectors in the vector set
     *
     * @return dimension count
     */
    int dimensions();

    /**
     * Retrieves the vector associated with an element
     *
     * @param element element name
     * @return list of vector coordinates
     */
    List<Double> getVector(String element);

    /**
     * Retrieves the raw vector associated with an element
     *
     * @param element element name
     * @return list of raw vector values
     */
    List<Object> getRawVector(String element);

    /**
     * Retrieves attributes of a vector set element
     *
     * @param element element name
     * @param clazz type for deserialization
     * @return deserialized attributes object
     */
    <T> T getAttributes(String element, Class<T> clazz);

    /**
     * Returns metadata and internal details about the vector set
     *
     * @return vector set information
     */
    VectorInfo getInfo();

    /**
     * Retrieves the neighbors of a specified element
     *
     * @param element element name
     * @return list of neighbor element names
     */
    List<String> getNeighbors(String element);

    /**
     * Retrieves the neighbors of a specified element with scores
     *
     * @param element element name
     * @return list of neighbor elements with scores
     */
    List<ScoredEntry<String>> getNeighborEntries(String element);

    /**
     * Returns a random element from the vector set
     *
     * @return random element name
     */
    String random();

    /**
     * Returns multiple random elements from the vector set
     *
     * @param count number of elements to return
     * @return list of random element names
     */
    List<String> random(int count);

    /**
     * Removes an element from the vector set
     *
     * @param element element name to remove
     * @return <code>true</code> if element was removed, <code>false</code> otherwise
     */
    boolean remove(String element);

    /**
     * Sets attributes for a vector set element
     *
     * @param element element name
     * @param attributes attributes object to serialize
     * @return <code>true</code> if attributes were set, <code>false</code> otherwise
     */
    boolean setAttributes(String element, Object attributes);

    /**
     * Retrieves elements similar to a given vector or element
     *
     * @param args vector similarity arguments
     * @return list of similar element names
     */
    List<String> getSimilar(VectorSimilarArgs args);

    /**
     * Retrieves elements similar to a given vector or element with scores
     *
     * @param args vector similarity arguments
     * @return list of similar elements with scores
     */
    List<ScoredEntry<String>> getSimilarEntries(VectorSimilarArgs args);
}
