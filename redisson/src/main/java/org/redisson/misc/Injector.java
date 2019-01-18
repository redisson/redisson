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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RInject;

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
            if (RedissonClient.class.isAssignableFrom(field.getType())
                    && field.isAnnotationPresent(RInject.class)) {
                field.setAccessible(true);
                try {
                    field.set(task, redisson);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }
    
}
