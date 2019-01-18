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
package org.redisson.client.protocol.convertor;

import org.redisson.api.RType;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class TypeConvertor implements Convertor<RType> {

    @Override
    public RType convert(Object obj) {
        String val = obj.toString();
        if ("string".equals(val)) {
            return RType.OBJECT;
        }
        if ("list".equals(val)) {
            return RType.LIST;
        }
        if ("set".equals(val)) {
            return RType.SET;
        }
        if ("zset".equals(val)) {
            return RType.ZSET;
        }
        if ("hash".equals(val)) {
            return RType.MAP;
        }
        if ("none".equals(val)) {
            return null;
        }

        throw new IllegalStateException("Can't recognize redis type: " + obj);
    }

}
