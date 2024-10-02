package org.redisson.api.options;

import org.redisson.api.RKeys;
import org.redisson.api.RType;

/**
 * {@link RKeys#getKeys()} method options
 *
 * @author Nikita Koksharov
 *
 */
public interface KeysScanOptions {

    /**
     * Creates the default options
     *
     * @return options instance
     */
    static KeysScanOptions defaults() {
        return new KeysScanParams();
    }

    /**
     * Defines the total amount of returned keys.
     *
     * @param value total amount of returned keys
     * @return options instance
     */
    KeysScanOptions limit(int value);

    /**
     * Defines the pattern that all keys should match.
     * Supported glob-style patterns:
     *  <p>
     *    h?llo matches hello, hallo and hxllo
     *    <p>
     *    h*llo matches to hllo and heeeello
     *    <p>
     *    h[ae]llo matches to hello and hallo, but not hillo     *
     *
     * @param value key pattern
     * @return options instance
     */
    KeysScanOptions pattern(String value);

    /**
     * Defines the amount of loaded keys per request.
     *
     * @param value amount of loaded keys per request
     * @return options instance
     */
    KeysScanOptions chunkSize(int value);

    /**
     * Defines the type of objects that all keys should match.
     *
     * @param value type of objects
     * @return options instance
     */
    KeysScanOptions type(RType value);

}
