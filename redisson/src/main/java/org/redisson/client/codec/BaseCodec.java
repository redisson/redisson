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

    public static Codec copy(ClassLoader classLoader, Codec codec) {
        if (codec instanceof StringCodec
                || codec instanceof ByteArrayCodec
                    || codec instanceof LocalCachedMessageCodec
                        || codec instanceof BitSetCodec
                            || codec instanceof JCacheEventCodec
                                || codec == null) {
            return codec;
        }

        try {
            return codec.getClass().getConstructor(ClassLoader.class, codec.getClass()).newInstance(classLoader, codec);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
