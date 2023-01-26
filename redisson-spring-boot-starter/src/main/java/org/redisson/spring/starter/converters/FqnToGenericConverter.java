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
package org.redisson.spring.starter.converters;

import org.springframework.core.convert.converter.Converter;

import java.lang.reflect.InvocationTargetException;

/**
 * Convert a fully qualified class name (FQN) to an instance.
 *
 * @param <T> the type that the FQN will be part of.
 */
public abstract class FqnToGenericConverter<T> implements Converter<String, T> {
    @Override
    public T convert(String sourceClassName) {
        try {
            Class<T> clazz = (Class<T>) Class.forName(sourceClassName);
            return clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
