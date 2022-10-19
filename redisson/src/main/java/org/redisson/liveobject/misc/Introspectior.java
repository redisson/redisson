/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.liveobject.misc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.api.annotation.RId;
import org.redisson.cache.LRUCacheMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class Introspectior {

    public static TypeDescription.ForLoadedType getTypeDescription(Class<?> c) {
        return new TypeDescription.ForLoadedType(c);
    }

    public static FieldList<FieldDescription.InDefinedShape> getFieldsWithAnnotation(Class<?> c, Class<? extends Annotation> a) {
        return getAllFields(c)
                .filter(ElementMatchers.isAnnotatedWith(a));
    }

    public static FieldList<FieldDescription.InDefinedShape> getAllFields(Class<?> cls) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            Collections.addAll(fields, c.getDeclaredFields());
        }
        return new FieldList.ForLoadedFields(fields);
    }


    private static final Map<Class<?>, String> ID_FIELD_NAME_CACHE = new LRUCacheMap<>(500, 0, 0);

    public static String getREntityIdFieldName(Class<?> cls) {
        String name = ID_FIELD_NAME_CACHE.get(cls);
        if (name == null) {
            name = Introspectior
                    .getFieldsWithAnnotation(cls, RId.class)
                    .getOnly()
                    .getName();
            ID_FIELD_NAME_CACHE.put(cls, name);
        }
        return name;
    }

}
