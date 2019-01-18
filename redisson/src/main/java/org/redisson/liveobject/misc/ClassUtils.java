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
/**
 * Copyright (c) 2006, Paul Speed
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2) Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3) Neither the names "Progeeks", "Meta-JB", nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.redisson.liveobject.misc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.redisson.api.RObject;
import org.redisson.cache.LRUCacheMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui) Modified
 */
public class ClassUtils {
    
    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Field field = getDeclaredField(obj.getClass(), fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            field.set(obj, value);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public static <T extends Annotation> T getAnnotation(Class<?> clazz, String fieldName, Class<T> annotationClass) {
        try {
            Field field = getDeclaredField(clazz, fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            return field.getAnnotation(annotationClass);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static <T extends Annotation> T getAnnotation(Class<?> clazz, Class<T> annotationClass) {
        for (Class<?> c : getClassHierarchy(clazz)) {
            if (c.getAnnotation(annotationClass) != null) {
                return c.getAnnotation(annotationClass);
            }
        }
        return null;
    }

    public static <T> T getField(Object obj, String fieldName) {
        try {
            Field field = getDeclaredField(obj.getClass(), fieldName);
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            return (T) field.get(obj);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Field getDeclaredField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        for (Class<?> c : getClassHierarchy(clazz)) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(fieldName)) {
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("No such field: " + fieldName);
    }
    
    private static final Map<Class<?>, Boolean> annotatedClasses = new LRUCacheMap<Class<?>, Boolean>(500, 0, 0);

    public static boolean isAnnotationPresent(Class<?> clazz, Class<? extends Annotation> annotation) {
        if (clazz.getName().startsWith("java.")) {
            return false;
        }
        
        Boolean isAnnotated = annotatedClasses.get(clazz);
        if (isAnnotated == null) {
            for (Class<?> c : getClassHierarchy(clazz)) {
                if (c.isAnnotationPresent(annotation)) {
                    annotatedClasses.put(clazz, true);
                    return true;
                }
            }
            annotatedClasses.put(clazz, false);
            return false;
        }
        return isAnnotated;
    }

    private static Iterable<Class<?>> getClassHierarchy(Class<?> clazz) {
        // Don't descend into hierarchy for RObjects
        if (Arrays.asList(clazz.getInterfaces()).contains(RObject.class)) {
            return Collections.<Class<?>>singleton(clazz);
        }
        List<Class<?>> classes = new ArrayList<Class<?>>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            classes.add(c);
        }
        return classes;
    }
    
    /**
     * Searches through all methods looking for one with the specified name that
     * will take the specified paramaters even if the parameter types are more
     * generic in the actual method implementation. This is similar to the
     * findConstructor() method and has the similar limitations that it doesn't
     * do a real widening scope search and simply processes the methods in
     * order.
     * 
     * @param type param
     * @param name of class
     * @param parms classes
     * 
     * @return Method object
     */
    public static Method searchForMethod(Class<?> type, String name, Class<?>[] parms) {
        try {
            return type.getMethod(name, parms);
        } catch (NoSuchMethodException e) {}
        Method[] methods = type.getMethods();
        for (int i = 0; i < methods.length; i++) {
            // Has to be named the same of course.
            if (!methods[i].getName().equals(name)) {
                continue;
            }

            Class<?>[] types = methods[i].getParameterTypes();
            // Does it have the same number of arguments that we're looking for.
            if (types.length != parms.length) {
                continue;
            }

            // Check for type compatibility
            if (areTypesCompatible(types, parms)) {
                return methods[i];
            }
        }
        return null;
    }

    private static boolean areTypesCompatible(Class<?>[] targets, Class<?>[] sources) {
        if (targets.length != sources.length) {
            return false;
        }

        for (int i = 0; i < targets.length; i++) {
            if (sources[i] == null) {
                continue;
            }

            if (!translateFromPrimitive(targets[i]).isAssignableFrom(sources[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * If this specified class represents a primitive type (int, float, etc.)
     * then it is translated into its wrapper type (Integer, Float, etc.). If
     * the passed class is not a primitive then it is just returned.
     * 
     * @param primitive class
     * @return class
     */
    private static Class<?> translateFromPrimitive(Class<?> primitive) {
        if (!primitive.isPrimitive()) {
            return primitive;
        }

        if (Boolean.TYPE.equals(primitive)) {
            return Boolean.class;
        }
        if (Character.TYPE.equals(primitive)) {
            return Character.class;
        }
        if (Byte.TYPE.equals(primitive)) {
            return Byte.class;
        }
        if (Short.TYPE.equals(primitive)) {
            return Short.class;
        }
        if (Integer.TYPE.equals(primitive)) {
            return Integer.class;
        }
        if (Long.TYPE.equals(primitive)) {
            return Long.class;
        }
        if (Float.TYPE.equals(primitive)) {
            return Float.class;
        }
        if (Double.TYPE.equals(primitive)) {
            return Double.class;
        }

        throw new RuntimeException("Error translating type:" + primitive);
    }
}
