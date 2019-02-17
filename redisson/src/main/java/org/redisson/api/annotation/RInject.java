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
package org.redisson.api.annotation;

import org.redisson.client.codec.Codec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies that the field value is filled up with RedissonClient and other Redisson supplied instances.
 * 
 * @author Nikita Koksharov
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface RInject {

    /**
     * Only used to signal {@link org.redisson.misc.Injector} to use default codec from its config.
     *
     * Maybe there is a better way to reduce the memory footprint, for now it is the best way to implement without
     * changes to the Config class.
     *
     */
    abstract class DefaultCodec implements Codec {
        private DefaultCodec() {}
    }

    /**
     * Shorthand for {@link #name()}. Ignored when  {@link #name()} is specified.
     * @see #name()
     */
    String value() default "";


    /**
     * The name of requested object.
     *
     * <b>Optional</b> for {@link org.redisson.api.RedissonClient RedissonClient} type, it is used to lookup the
     * instance in CDI framework if configured, otherwise ignored.
     * <b>Required</b> for other types.
     *
     * For Spring framework, it is possible to SpEL expression as name. Hence it is possible to use "@myBean" as value
     * to find the bean named "myBean" from context as injection candidate.
     */
    String name() default "";

    /**
     * The codec used for requested object. Will try to find existing instance of given class via
     * {@link org.redisson.codec.ReferenceCodecProvider#getCodec(Class) ReferenceCodecProvider.getCodec(Class)} method.
     *
     * <b>Ignored</b> when the type is {@link org.redisson.api.RedissonClient RedissonClient}.
     * <b>Optional</b> for other types.
     *
     */
    Class<? extends Codec> codec() default DefaultCodec.class;


    /**
     * Bean name used to lookup the Redisson instance in CDI framework.
     *
     * <b>Ignored</b> when the type is {@link org.redisson.api.RedissonClient RedissonClient}.
     * <b>Optional</b> for other types.
     */
    String redissonBeanRef() default "";
}
