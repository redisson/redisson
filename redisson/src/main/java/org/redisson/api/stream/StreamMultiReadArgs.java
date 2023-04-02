/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.api.stream;

import org.redisson.api.StreamMessageId;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Arguments object for RStream.read() methods.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamMultiReadArgs {

    /**
     * Defines stream data size limit.
     *
     * @param count - stream data size limit
     * @return arguments object
     */
    StreamMultiReadArgs count(int count);

    /**
     * Defines time interval to wait for stream data availability.
     *
     * @param timeout - timeout duration
     * @return arguments object
     */
    StreamMultiReadArgs timeout(Duration timeout);

    /**
     * Defines last stream ids received from all Streams
     * including current one.
     * <p>
     * Read stream data from all defined streams
     * with ids greater than defined ids.
     *
     * @param id1 - last stream id of current stream
     * @param stream2 - name of 2nd stream
     * @param id2 - last stream id of 2nd stream
     * @return arguments object
     */
    static StreamMultiReadArgs greaterThan(StreamMessageId id1,
                                           String stream2, StreamMessageId id2) {
        return greaterThan(id1, Collections.singletonMap(stream2, id2));
    }

    /**
     * Defines last stream ids received from all Streams
     * including current one.
     * <p>
     * Read stream data from all defined streams
     * with ids greater than defined ids.
     *
     * @param id1 - last stream id of current stream
     * @param stream2 - name of 2nd stream
     * @param id2 - last stream id of 2nd stream
     * @param stream3 - name of 3rd stream
     * @param id3 - last stream id of 3rd stream
     * @return arguments object
     */
    static StreamMultiReadArgs greaterThan(StreamMessageId id1,
                                           String stream2, StreamMessageId id2,
                                           String stream3, StreamMessageId id3) {
        Map<String, StreamMessageId> map = new HashMap<>();
        map.put(stream2, id2);
        map.put(stream3, id3);
        return greaterThan(id1, map);
    }

    /**
     * Defines last stream ids received from all Streams
     * including current one.
     * <p>
     * Read stream data from all defined streams
     * with ids greater than defined ids.
     *
     * @param id1 - last stream id of current stream
     * @param stream2 - name of 2nd stream
     * @param id2 - last stream id of 2nd stream
     * @param stream3 - name of 3rd stream
     * @param id3 - last stream id of 3rd stream
     * @param stream4 - name of 4th stream
     * @param id4 - last stream id of 4th stream
     * @return arguments object
     */
    static StreamMultiReadArgs greaterThan(StreamMessageId id1,
                                           String stream2, StreamMessageId id2,
                                           String stream3, StreamMessageId id3,
                                           String stream4, StreamMessageId id4) {
        Map<String, StreamMessageId> map = new HashMap<>();
        map.put(stream2, id2);
        map.put(stream3, id3);
        map.put(stream4, id4);
        return greaterThan(id1, map);
    }

    /**
     * Defines last stream ids received from all Streams
     * including current one.
     * <p>
     * Read stream data from all defined streams
     * with ids greater than defined ids.
     *
     * @param id1 - last stream id of current stream
     * @param stream2 - name of 2nd stream
     * @param id2 - last stream id of 2nd stream
     * @param stream3 - name of 3rd stream
     * @param id3 - last stream id of 3rd stream
     * @param stream4 - name of 4th stream
     * @param id4 - last stream id of 4th stream
     * @param stream5 - name of 4th stream
     * @param id5 - last stream id of 4th stream
     * @return arguments object
     */
    static StreamMultiReadArgs greaterThan(StreamMessageId id1,
                                           String stream2, StreamMessageId id2,
                                           String stream3, StreamMessageId id3,
                                           String stream4, StreamMessageId id4,
                                           String stream5, StreamMessageId id5) {
        Map<String, StreamMessageId> map = new HashMap<>();
        map.put(stream2, id2);
        map.put(stream3, id3);
        map.put(stream4, id4);
        map.put(stream5, id5);
        return greaterThan(id1, map);
    }

    /**
     * Defines last stream ids received from all Streams
     * including current one.
     * <p>
     * Read stream data from all defined streams
     * with ids greater than defined ids.
     *
     * @param id1 - last stream id of current stream
     * @param offsets - last stream id mapped by stream name
     * @return arguments object
     */
    static StreamMultiReadArgs greaterThan(StreamMessageId id1, Map<String, StreamMessageId> offsets) {
        return new StreamMultiReadParams(id1, offsets);
    }

}
