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

import java.io.IOException;
import java.lang.reflect.Field;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheKeysFactory extends DefaultCacheKeysFactory {

    private final Codec codec;

    public RedissonCacheKeysFactory(Codec codec) {
        this.codec = codec;
    }

    @Override
    public Object createCollectionKey(Object id, CollectionPersister persister, SessionFactoryImplementor factory, String tenantIdentifier) {
        try {
            String[] parts = persister.getRole().split("\\.");
            Field f = ReflectHelper.findField(id.getClass(), parts[parts.length - 1]);

            Object prev = f.get(id);
            f.set(id, null);
            ByteBuf state = codec.getMapKeyEncoder().encode(id);
            Object newId = codec.getMapKeyDecoder().decode(state, null);
            state.release();
            f.set(id, prev);
            return super.createCollectionKey(newId, persister, factory, tenantIdentifier);
        } catch (PropertyNotFoundException e) {
            return super.createCollectionKey(id, persister, factory, tenantIdentifier);
        } catch (IllegalAccessException | IOException e) {
            throw new IllegalStateException(e);
        }
    }


}
