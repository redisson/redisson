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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
public abstract class AbstractInjectionContext implements InjectionContext {

    /**
     *  (([A-Za-z]+)) Matches alpha characters for one or more times at the beginning as the first caption group
     *  \\(' Should have left bracket and a single quote after the word
     *  [^] for negative selection
     *  ([^'()]+) within the capture group shoud not be any more brackets or quotes. For one or more times.
     *  '\\) Should have a single quote and a right bracket to end the expression.
     *
     *  Example: RMap('myMap')
     */
    private static final Pattern REDISSON_EXPRESSION_PATTERN = Pattern.compile("^([A-Za-z]+)\\('([^'()]+)'\\)$");

    @Override
    public <T> T resolve(Class<T> fieldType, RInject rInject) {
        String name = rInject.name();
        if ("".equals(name)) {
            name = rInject.value();
        }
        RedissonClient redissonClient = resolveRedisson(name, fieldType, rInject);

        if (RedissonClient.class.isAssignableFrom(fieldType)) {
            return (T) redissonClient;
        }
        if ("".equals(name)) {
            throw new IllegalStateException("Name in RInject is required for class " + fieldType.getName());
        }

        if (redissonClient == null) {
            return null;
        }

        return resolveRedissonObjects(redissonClient, fieldType, name, rInject.codec());
    }

    protected abstract RedissonClient resolveRedisson(String expectedName, Class targetType, RInject rInject);

    //TODO: Support Redisson services lookup
    protected <T, C extends Codec > T resolveRedissonObjects(RedissonClient redissonClient, Class<T> expected, String name, Class<C> codecClass) {

        final Matcher redissonExpressionMatcher = getRedissonExpressionMatcher(name);
        if (redissonExpressionMatcher.matches()) {
            final String objName = redissonExpressionMatcher.group(1);
            final Class<?> supportedTypes = RedissonObjectBuilder.getSupportedTypes(objName);
            if (supportedTypes == null) {
                throw new IllegalArgumentException("Redisson does not support the \"" + objName + "\" mentioned in \"" + name + "\"");
            }
            expected = (Class<T>) supportedTypes;
            name = redissonExpressionMatcher.group(2);
        }

        if (expected.isAnnotationPresent(REntity.class)) {
            return redissonClient.getLiveObjectService().get(expected, name);
        } else {
            RedissonObjectBuilder builder = ((Redisson) redissonClient).getCommandExecutor().getObjectBuilder();
            Codec codec;
            if (RInject.DefaultCodec.class.isAssignableFrom(codecClass)) codec = null;
            else codec = builder.getReferenceCodecProvider().getCodec(codecClass);
            try {
                return (T) builder.createRObject(redissonClient, builder.tryTranslatedTypes(expected), name, codec);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create RObject of type " + expected.getName(), e);
            }
        }
    }

    protected Matcher getRedissonExpressionMatcher(String expr) {
        return REDISSON_EXPRESSION_PATTERN.matcher(expr);
    }
}
