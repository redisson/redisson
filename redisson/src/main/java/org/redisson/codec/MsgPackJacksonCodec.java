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
package org.redisson.codec;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MsgPack binary codec
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class MsgPackJacksonCodec extends JsonJacksonCodec {

    public MsgPackJacksonCodec() {
        super(new ObjectMapper(new MessagePackFactory()));
    }
    
    public MsgPackJacksonCodec(ClassLoader classLoader) {
        super(createObjectMapper(classLoader, new ObjectMapper(new MessagePackFactory())));
    }
    
    public MsgPackJacksonCodec(ClassLoader classLoader, MsgPackJacksonCodec codec) {
        super(createObjectMapper(classLoader, codec.mapObjectMapper.copy()));
    }
    
}
