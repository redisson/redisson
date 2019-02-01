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
import org.redisson.api.RObject;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RInject;
import org.redisson.client.codec.Codec;
import org.redisson.liveobject.core.RedissonObjectBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class Injector {

    public static void inject(Object task, RedissonClient redisson) {
        List<Field> allFields = new ArrayList<Field>();
        Class<?> clazz = task.getClass();
        while (true) {
            if (clazz != null) {
                Field[] fields = clazz.getDeclaredFields();
                allFields.addAll(Arrays.asList(fields));
            } else {
                break;
            }
            if (clazz.getSuperclass() != Object.class) {
                clazz = clazz.getSuperclass();
            } else {
                clazz = null;
            }
        }

        for (Field field : allFields) {
            if (!field.isAnnotationPresent(RInject.class)) {
                continue;
            }
            Class<?> fieldType = field.getType();
            RInject rInject = field.getAnnotation(RInject.class);
            if (RedissonClient.class.isAssignableFrom(fieldType)) {
                try {
                    ClassUtils.trySetFieldWithSetter(task, field, redisson);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Failed to set RedissonClient instance", e);
                }
                continue;
            }

            String name = rInject.name();
            if ("".equals(name)) {
                throw new IllegalStateException("Name in RInject is required for class " + fieldType.getName());
            }
            if (fieldType.isAnnotationPresent(REntity.class)) {
                Object rlo = redisson == null
                        ? null
                        : redisson.getLiveObjectService().get(fieldType, name);
                try {
                    ClassUtils.trySetFieldWithSetter(task, field, rlo);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Failed to set REntity of type " + fieldType.getName(), e);
                }
            } else {
                RObject rObject;
                if (redisson == null) {
                    rObject = null;
                } else {
                    RedissonObjectBuilder builder = ((Redisson) redisson).getCommandExecutor().getObjectBuilder();
                    Class codecClass = rInject.codec();
                    Codec codec = RInject.DefaultCodec.class.isAssignableFrom(codecClass) ? null : builder.getReferenceCodecProvider().getCodec(codecClass);
                    try {
                        rObject = builder.createRObject(redisson, (Class<RObject>) fieldType, name, codec);
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to create RObject of type " + fieldType.getName(), e);
                    }
                }
                try {
                    ClassUtils.trySetFieldWithSetter(task, field, rObject);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Failed to set RObject of type " + fieldType.getName(), e);
                }
            }
        }
    }

}
