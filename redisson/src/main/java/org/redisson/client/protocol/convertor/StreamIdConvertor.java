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

import org.redisson.api.StreamMessageId;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamIdConvertor implements Convertor<StreamMessageId> {

    public static final StreamIdConvertor INSTANCE = new StreamIdConvertor();
    
    @Override
    public StreamMessageId convert(Object id) {
        String[] parts = id.toString().split("-");
        return new StreamMessageId(Long.valueOf(parts[0]), Long.valueOf(parts[1]));
    }

}
