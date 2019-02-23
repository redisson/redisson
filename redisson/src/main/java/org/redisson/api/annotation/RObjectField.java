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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;
import org.redisson.liveobject.resolver.DefaultNamingScheme;
import org.redisson.liveobject.resolver.NamingScheme;

/**
 * By default <code>namingScheme</code> and/or <code>codec</code> parameters specified in {@link REntity}
 * are applied for each Live Object field. 
 * 
 * This annotation allows to specify custom <code>namingScheme</code> and/or <code>codec</code> parameters 
 * for any Live Object field except that marked with {@link RId}.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RObjectField{

    /**
     * (Optional) Live Object naming scheme. Defines how to assign key names for each instance of this class. 
     * Used to create a reference to an existing Live Object and materialising a new one in redis. 
     * Defaults to {@link DefaultNamingScheme} implementation.
     * 
     * @return scheme
     */
    Class<? extends NamingScheme> namingScheme() default DefaultNamingScheme.class;

    /**
     * (Optional) Live Object state codec.
     * <code>null</code> means to use codec specified in Redisson configuration
     * 
     * @return codec
     */
    Class<? extends Codec> codec() default DEFAULT.class;
    
    final class DEFAULT extends BaseCodec {
        @Override
        public Decoder<Object> getValueDecoder() {
            return null;
        }

        @Override
        public Encoder getValueEncoder() {
            return null;
        }
    }
    
}
