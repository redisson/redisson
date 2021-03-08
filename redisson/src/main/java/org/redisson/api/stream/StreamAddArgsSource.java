package org.redisson.api.stream;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamAddArgsSource<K, V> {

    StreamAddParams<K, V> getParams();

}
