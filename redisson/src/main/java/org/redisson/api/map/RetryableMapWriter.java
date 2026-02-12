/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api.map;

import org.redisson.api.MapOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class RetryableMapWriter<K, V> implements MapWriter<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RetryableMapWriter.class);

    private final MapOptions<K, V> options;
    private final MapWriter<K, V> mapWriter;

    public RetryableMapWriter(MapOptions<K, V> options, MapWriter<K, V> mapWriter) {
        this.options = options;
        this.mapWriter = mapWriter;
    }

    @Override
    public void write(Map<K, V> addedMap) {
        //execute at least once
        int leftAddAttempts = Math.max(1, options.getWriterRetryAttempts());
        while (leftAddAttempts > 0) {
            try {
                //do write
                mapWriter.write(addedMap);
                break;
            } catch (Exception exception) {
                if (--leftAddAttempts == 0) {
                    throw exception;
                } else {
                    log.warn("Unable to add keys: {}, will retry after {}ms", addedMap, options.getWriterRetryInterval(), exception);
                    try {
                        Thread.sleep(options.getWriterRetryInterval());
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }
    }

    @Override
    public void delete(Collection<K> keys) {
        //execute at least once
        int leftDeleteAttempts = Math.max(1, options.getWriterRetryAttempts());
        while (leftDeleteAttempts > 0) {
            try {
                //do delete
                mapWriter.delete(keys);
                break;
            } catch (Exception exception) {
                if (--leftDeleteAttempts == 0) {
                    throw exception;
                } else {
                    log.warn("Unable to delete keys: {}, will retry after {}ms", keys, options.getWriterRetryInterval(), exception);
                    try {
                        Thread.sleep(options.getWriterRetryInterval());
                    } catch (InterruptedException ignore) {
                    }
                }
            }
        }
    }
}
