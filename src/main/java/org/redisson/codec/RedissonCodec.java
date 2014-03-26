/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.codec;

import java.nio.ByteBuffer;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonCodec {

    Object decodeKey(ByteBuffer bytes);

    Object decodeValue(ByteBuffer bytes);

    byte[] encodeKey(Object key);

    byte[] encodeValue(Object value);

    byte[] encodeMapValue(Object value);

    byte[] encodeMapKey(Object key);

    Object decodeMapValue(ByteBuffer bytes);

    Object decodeMapKey(ByteBuffer bytes);

}
