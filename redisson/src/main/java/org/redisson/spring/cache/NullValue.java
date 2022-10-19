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
package org.redisson.spring.cache;

import java.io.Serializable;

import org.springframework.cache.Cache.ValueWrapper;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class NullValue implements ValueWrapper, Serializable {

    private static final long serialVersionUID = -8310337775544536701L;
    
    public static final NullValue INSTANCE = new NullValue();

    @Override
    public Object get() {
        return null;
    }

}
