package org.redisson.core;

import java.util.Collection;

import io.netty.util.concurrent.Future;

public interface RKeys {

    String randomKey();

    Future<String> randomKeyAsync();

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
     * Find keys by key search pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    Future<Collection<String>> findKeysByPatternAsync(String pattern);

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
     * Delete multiple objects by a key pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    Future<Long> deleteByPatternAsync(String pattern);

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    long delete(String ... keys);

    /**
     * Delete multiple objects by name in async mode
     *
     * @param keys - object names
     * @return
     */
    Future<Long> deleteAsync(String ... keys);

}
