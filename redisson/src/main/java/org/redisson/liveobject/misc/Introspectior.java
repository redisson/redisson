/**
 * Copyright 2016 Nikita Koksharov
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
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatchers;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class Introspectior {

    public static TypeDescription.ForLoadedType getTypeDescription(Class<?> c) {
        return new TypeDescription.ForLoadedType(c);
    }

    public static MethodDescription getMethodDescription(Class<?> c, String method) {
        if (method == null || method.isEmpty()) {
            return null;
        }
        return getTypeDescription(c)
                .getDeclaredMethods()
                .filter(ElementMatchers.hasMethodName(method))
                .getOnly();
    }

    public static FieldList<FieldDescription.InDefinedShape> getFieldsDescription(Class<?> c) {
        return getTypeDescription(c)
                .getDeclaredFields();
                
    }
    
    public static FieldDescription getFieldDescription(Class<?> c, String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }
        return getTypeDescription(c)
                .getDeclaredFields()
                .filter(ElementMatchers.named(field))
                .getOnly();
    }

    public static FieldList<FieldDescription.InDefinedShape> getFieldsWithAnnotation(Class<?> c, Class<? extends Annotation> a) {
        return getTypeDescription(c)
                .getDeclaredFields()
                .filter(ElementMatchers.isAnnotatedWith(a));
    }
}
