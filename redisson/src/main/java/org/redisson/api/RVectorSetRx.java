package org.redisson.api;

import java.util.List;
import org.redisson.api.vector.VectorAddArgs;
import org.redisson.api.vector.VectorInfo;
import org.redisson.api.vector.VectorSimilarArgs;
import org.redisson.client.protocol.ScoredEntry;
import io.reactivex.rxjava3.core.Single;

/**
 * RxJava interface for Redis Vector Set
 *
 * @author Nikita Koksharov
 *
 */
public interface RVectorSetRx extends RExpirableRx {
    /**
     * Adds an element to the vector set
     *
     * @param args vector add arguments
     * @return <code>true</code> if element was added and <code>false</code> if updated
     */
    Single<Boolean> add(VectorAddArgs args);

    /**
     * Returns the number of elements in the vector set
     *
     * @return cardinality of the vector set
     */
    Single<Integer> size();

    /**
     * Returns the dimension of vectors in the vector set
     *
     * @return dimension count
     */
    Single<Integer> dimensions();

    /**
     * Retrieves the vector associated with an element
     *
     * @param element element name
     * @return list of vector coordinates
     */
    Single<List<Double>> getVector(String element);

    /**
     * Retrieves the raw vector associated with an element
     *
     * @param element element name
     * @return list of raw vector values
     */
    Single<List<Object>> getRawVector(String element);

    /**
     * Retrieves attributes of a vector set element
     *
     * @param element element name
     * @param clazz class type for deserialization
     * @return deserialized attributes object
     */
    <T> Single<T> getAttributes(String element, Class<T> clazz);

    /**
     * Returns metadata and internal details about the vector set
     *
     * @return vector set information
     */
    Single<VectorInfo> getInfo();

    /**
     * Retrieves the neighbors of a specified element
     *
     * @param element element name
     * @return list of neighbor element names
     */
    Single<List<String>> getNeighbors(String element);

    /**
     * Retrieves the neighbors of a specified element with scores
     *
     * @param element element name
     * @return list of neighbor elements with scores
     */
    Single<List<ScoredEntry<String>>> getNeighborEntries(String element);

    /**
     * Returns a random element from the vector set
     *
     * @return random element name
     */
    Single<String> random();

    /**
     * Returns multiple random elements from the vector set
     *
     * @param count number of elements to return
     * @return list of random element names
     */
    Single<List<String>> random(int count);

    /**
     * Removes an element from the vector set
     *
     * @param element element name to remove
     * @return <code>true</code> if element was removed, <code>false</code> otherwise
     */
    Single<Boolean> remove(String element);

    /**
     * Sets attributes for a vector set element
     *
     * @param element element name
     * @param attributes attributes object to serialize
     * @return <code>true</code> if attributes were set, <code>false</code> otherwise
     */
    Single<Boolean> setAttributes(String element, Object attributes);

    /**
     * Retrieves elements similar to a given vector or element
     *
     * @param args vector similarity arguments
     * @return list of similar element names
     */
    Single<List<String>> getSimilar(VectorSimilarArgs args);

    /**
     * Retrieves elements similar to a given vector or element with scores
     *
     * @param args vector similarity arguments
     * @return list of similar elements with scores
     */
    Single<List<ScoredEntry<String>>> getSimilarEntries(VectorSimilarArgs args);
}
