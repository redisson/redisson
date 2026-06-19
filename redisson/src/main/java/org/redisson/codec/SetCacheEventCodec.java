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
package org.redisson.codec;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.Decoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Codec used to decode expired entry events published by
 * {@link org.redisson.eviction.SetCacheEvictionTask}. Each message contains
 * a single set value encoded with the set's value codec.
 *
 * @author Nikita Koksharov
 *
 */
public class SetCacheEventCodec extends BaseEventCodec {

    private final Decoder<Object> decoder = (buf, state) -> {
        List<Object> result = new ArrayList<Object>(1);

        Object value = SetCacheEventCodec.this.decode(buf, state, codec.getValueDecoder());
        result.add(value);

        return result;
    };

    public SetCacheEventCodec(Codec codec, OSType osType) {
        super(codec, osType);
    }

    public SetCacheEventCodec(ClassLoader classLoader, SetCacheEventCodec codec) {
        super(newCodec(classLoader, codec), codec.osType);
    }

    private static Codec newCodec(ClassLoader classLoader, SetCacheEventCodec codec) {
        try {
            return codec.codec.getClass().getConstructor(ClassLoader.class, codec.codec.getClass()).newInstance(classLoader, codec.codec);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

}
