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
package org.redisson.liveobject.resolver;

import org.redisson.client.codec.Codec;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public interface NamingScheme {

    String getName(Class<?> entityClass, Class<?> idFieldClass, String idFieldName, Object idValue);
    
    String getIndexName(Class<?> entityClass, String fieldName);
    
    String getFieldReferenceName(Class<?> entityClass, Object idValue, Class<?> fieldClass, String fieldName, Object fieldValue);

    String resolveClassName(String name);

    String resolveIdFieldName(String name);

    Object resolveId(String name);
    
    Codec getCodec();
    
}
