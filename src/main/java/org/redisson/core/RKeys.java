package org.redisson.core;

import java.util.Collection;

public interface RKeys extends RKeysAsync {

    /**
     * Get keys by pattern using iterator. Keys traversing with SCAN operation
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @return
     */
    Iterable<String> keysIterable(String pattern);

    /**
     * Get keys using iterator. Keys traversing with SCAN operation
     *
     * @return
     */
    Iterable<String> keysIterable();

    /**
     * Get random key
     *
     * @return
     */
    String randomKey();

    /**
     * Find keys by key search pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    Collection<String> findKeysByPattern(String pattern);

    /**
     * Delete multiple objects by a key pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    long deleteByPattern(String pattern);

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    long delete(String ... keys);

}
