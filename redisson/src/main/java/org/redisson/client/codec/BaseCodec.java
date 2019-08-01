/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Arrays;
import java.util.List;

import org.redisson.cache.LocalCachedMessageCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.jcache.JCacheEventCodec;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseCodec implements Codec {

    public static final List<Class<?>> SKIPPED_CODECS = Arrays.asList(StringCodec.class, 
            ByteArrayCodec.class, LocalCachedMessageCodec.class, BitSetCodec.class, JCacheEventCodec.class);
    
    public static Codec copy(ClassLoader classLoader, Codec codec) throws ReflectiveOperationException {
        if (codec == null) {
            return codec;
        }

        for (Class<?> clazz : SKIPPED_CODECS) {
            if (clazz.isAssignableFrom(codec.getClass())) {
                return codec;
            }
        }

        return codec.getClass().getConstructor(ClassLoader.class, codec.getClass()).newInstance(classLoader, codec);
    }
    
    @Override
    public Decoder<Object> getMapValueDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapValueEncoder() {
        return getValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return getValueDecoder();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return getValueEncoder();
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    public String toString() {
        return getClass().getName();
    }
    
}
