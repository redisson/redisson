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
package org.redisson.misc;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RInject;
import org.redisson.client.codec.Codec;

import java.lang.reflect.Field;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
public abstract class AbstractInjectionContext implements InjectionContext {

    @Override
    public Object resolve(Object target, Field field, RInject rInject) {
        Class<?> fieldType = field.getType();
        String name = rInject.name();
        if ("".equals(name)) {
            name = rInject.value();
        }
        RedissonClient redissonClient = resolveRedisson(name, fieldType, rInject);

        if (RedissonClient.class.isAssignableFrom(fieldType)) {
            return redissonClient;
        }
        if ("".equals(name)) {
            throw new IllegalStateException("Name in RInject is required for class " + fieldType.getName());
        }

        if (redissonClient == null) {
            return null;
        }
        try {
            return resolveRedissonObjects(redissonClient, fieldType, name, rInject.codec());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to resolve RedissonObjects for field \"" + field.getName()
                            + "\" in class " + target.getClass().getName()
                            + " with annotation " + rInject, e);
        }
    }

    protected abstract RedissonClient resolveRedisson(String expectedName, Class targetType, RInject rInject);

    //TODO: Support Redisson services lookup
    protected <T, C extends Codec > T resolveRedissonObjects(RedissonClient redissonClient, Class<T> expected, String name, Class<C> codecClass) {
        if (expected.isAnnotationPresent(REntity.class)) {
            return redissonClient.getLiveObjectService().get(expected, name);
        } else {
            RedissonObjectBuilder builder = ((Redisson) redissonClient).getCommandExecutor().getObjectBuilder();
            Codec codec = RInject.DefaultCodec.class.isAssignableFrom(codecClass) ? null : builder.getReferenceCodecProvider().getCodec(codecClass);
            try {
                return (T) builder.createRObject(redissonClient, builder.getTranslatedTypes(expected), name, codec);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create RObject of type " + expected.getName(), e);
            }
        }
    }

}
