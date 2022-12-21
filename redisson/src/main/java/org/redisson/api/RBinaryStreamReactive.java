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
package org.redisson.api;

import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/**
 * Binary stream holder stores a sequence of bytes.
 * Maximum size of stream is limited to 512Mb.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RBinaryStreamReactive extends RBucketReactive<byte[]> {

    /**
     * Returns current channel's position
     *
     * @return current position
     */
    long position();

    /**
     * Sets channel's position
     *
     * @param newPosition - new position
     */
    void position(long newPosition);

    /**
     * Reads a sequence of bytes into defined buffer.
     *
     * @param buf buffer object into which bytes are read
     * @return amount of read bytes
     */
    Mono<Integer> read(ByteBuffer buf);

    /**
     * Writes a sequence of bytes from defined buffer.
     *
     * @param  buf buffer object from which bytes are transferred
     * @return amount of written bytes
     */
    Mono<Integer> write(ByteBuffer buf);

}
