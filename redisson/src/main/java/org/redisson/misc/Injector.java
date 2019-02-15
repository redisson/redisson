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

import org.redisson.api.annotation.RInject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
public class Injector {

    public static void inject(Object task, InjectionContext injectionContext) {
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
        doInjection(allFields, task, injectionContext);
    }

    private static void doInjection(List<Field> fields, Object target, InjectionContext injectionContext) {
        for (Field field : fields) {
            if (!field.isAnnotationPresent(RInject.class)) {
                continue;
            }

            Class<?> fieldType = field.getType();
            RInject rInject = field.getAnnotation(RInject.class);
            try {
                Object obj = injectionContext.resolve(fieldType, rInject);
                try {
                    ClassUtils.trySetFieldWithSetter(target, field, obj);
                } catch (IllegalStateException | IllegalArgumentException e) {
                    throw new IllegalStateException("Failed to set instance of type " + fieldType.getName() + " for field named " + field.getName(), e);
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to resolve RedissonObjects for field \"" + field.getName()
                                + "\" in class " + target.getClass().getName()
                                + " with annotation " + rInject, e);
            }
        }
    }
    
}
