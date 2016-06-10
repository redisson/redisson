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
package org.redisson.liveobject;

import io.netty.util.internal.PlatformDependent;
import java.util.concurrent.ConcurrentMap;
import org.redisson.client.codec.Codec;
import org.redisson.core.RObject;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class DefaultCodecProvider implements CodecProvider {

    public final ConcurrentMap<Class<? extends Codec>, Codec> codecCache = PlatformDependent.newConcurrentHashMap();

    @Override
    public Codec getCodec(Class<? extends Codec> codecClass) {
        if (!codecCache.containsKey(codecClass)) {
            try {
                codecCache.putIfAbsent(codecClass, codecClass.newInstance());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return codecCache.get(codecClass);
    }

    @Override
    public Codec getCodec(Class<? extends Codec> codecClass, Class<? extends RObject> rObjectClass, String name) {
        if (rObjectClass.isInterface()) {
            throw new IllegalArgumentException("Cannot lookup an interface class of RObject " + rObjectClass.getCanonicalName() + ". Concrete class only.");
        }
        return getCodec(codecClass);
    }

    @Override
    public Codec getCodec(Class<? extends Codec> codecClass, RObject rObject, String name) {
        return getCodec(codecClass, rObject.getClass(), name);
    }

    @Override
    public void registerCodec(Class<? extends Codec> cls, Codec codec) {
        codecCache.putIfAbsent(cls, codec);
    }

    @Override
    public void registerCodec(Class<? extends Codec> codecClass, Class<? extends RObject> rObjectClass, String name, Codec codec) {
        if (rObjectClass.isInterface()) {
            throw new IllegalArgumentException("Cannot register an interface class of RObject " + rObjectClass.getCanonicalName() + ". Concrete class only.");
        }
        registerCodec(codecClass, codec);
    }

    @Override
    public void registerCodec(Class<? extends Codec> codecClass, RObject rObject, String name, Codec codec) {
        registerCodec(codecClass, rObject.getClass(), name, codec);
    }

}
