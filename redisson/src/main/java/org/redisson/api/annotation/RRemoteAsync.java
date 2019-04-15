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

/**
 * Annotation used to mark interface as asynchronous 
 * client interface for remote service interface. 
 * <p>
 * All method signatures must match with remote service interface,
 * but return type must be <code>org.redisson.api.RFuture</code>.
 * <p>
 * It's not necessary to add all methods from remote service.
 * Add only those which are needed. 
 * 
 * @see org.redisson.api.RFuture
 * 
 * @author Nikita Koksharov
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RRemoteAsync {

    /**
     * Remote interface class used to register
     * 
     * @return class used to register
     */
    Class<?> value();
    
}
