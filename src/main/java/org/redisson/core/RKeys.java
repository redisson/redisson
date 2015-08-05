package org.redisson.core;

import java.util.Collection;

public interface RKeys extends RKeysAsync {

    Iterable<String> keysIterable();

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
