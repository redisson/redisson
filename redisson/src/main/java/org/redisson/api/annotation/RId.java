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

import org.redisson.liveobject.resolver.LongGenerator;
import org.redisson.liveobject.resolver.RIdResolver;
import org.redisson.liveobject.resolver.RequiredIdResolver;
import org.redisson.liveobject.resolver.UUIDGenerator;

/**
 * Specifies that the field is a Live Object's id field.
 * Only single field could be specified per class. 
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface RId {
    
    /**
     * (Optional) Live Object id generator. By default id is required to be fill during object creation.
     * 
     * @see UUIDGenerator
     * @see LongGenerator
     */
    Class<? extends RIdResolver> generator() default RequiredIdResolver.class;
    
}
