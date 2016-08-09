/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.client.codec;

import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

/**
 * Redis codec interface
 *
 * @author Nikita Koksharov
 *
 */
public interface Codec {

    /**
     * Returns object decoder used for hash map values in HMAP Redis structure
     *
     * @return
     */
    Decoder<Object> getMapValueDecoder();

    /**
     * Returns object encoder used for hash map values in HMAP Redis structure
     *
     * @return
     */
    Encoder getMapValueEncoder();

    /**
     * Returns object decoder used for hash map keys in HMAP Redis structure
     *
     * @return
     */
    Decoder<Object> getMapKeyDecoder();

    /**
     * Returns object encoder used for hash map keys in HMAP Redis structure
     *
     * @return
     */
    Encoder getMapKeyEncoder();

    /**
     * Returns object decoder used for any objects stored Redis structure except HMAP
     *
     * @return
     */
    Decoder<Object> getValueDecoder();

    /**
     * Returns object encoder used for any objects stored Redis structure except HMAP
     *
     * @return
     */
    Encoder getValueEncoder();

}
