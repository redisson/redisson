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
package org.redisson.hibernate;

import io.netty.buffer.ByteBuf;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.cache.internal.DefaultCacheKeysFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.persister.collection.CollectionPersister;
import org.redisson.client.codec.Codec;
import org.redisson.misc.Tuple;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheKeysFactory extends DefaultCacheKeysFactory {

    private final Map<Tuple<Class<?>, String>, Optional<Field>> cache = new ConcurrentHashMap<>();

    private final Codec codec;

    public RedissonCacheKeysFactory(Codec codec) {
        this.codec = codec;
    }

    @Override
    public Object createCollectionKey(Object id, CollectionPersister persister, SessionFactoryImplementor factory, String tenantIdentifier) {
        String[] parts = persister.getRole().split("\\.");
        String role = parts[parts.length - 1];

        Optional<Field> fo = cache.computeIfAbsent(new Tuple<>(id.getClass(), role), k -> {
            try {
                return Optional.of(ReflectHelper.findField(k.getT1(), k.getT2()));
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        if (!fo.isPresent()) {
            return super.createCollectionKey(id, persister, factory, tenantIdentifier);
        }

        try {
            Field f = fo.get();
            Object prev = f.get(id);
            f.set(id, null);
            ByteBuf state = null;
            Object newId = null;
            try {
                state = codec.getMapKeyEncoder().encode(id);
                newId = codec.getMapKeyDecoder().decode(state, null);
            } finally {
                f.set(id, prev);
                if (state != null) {
                    state.release();
                }
            }
            return super.createCollectionKey(newId, persister, factory, tenantIdentifier);
        } catch (IllegalAccessException | IOException e) {
            throw new IllegalStateException(e);
        }
    }


}
