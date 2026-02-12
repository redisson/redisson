/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.io.IOException;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class DefaultNamingScheme extends AbstractNamingScheme implements NamingScheme {

    public DefaultNamingScheme(Codec codec) {
        super(codec);
    }

    @Override
    public String getNamePattern(Class<?> entityClass) {
        return "redisson_live_object:{" + "*" + "}:" + entityClass.getName();
    }

    @Override
    public String getName(Class<?> entityClass, Object idValue) {
        try {
            String encode = bytesToHex(codec.getMapKeyEncoder().encode(idValue));
            return "redisson_live_object:{"+ encode + "}:" + entityClass.getName();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable create name for '" + entityClass + "' with id:" + idValue, ex);
        }
    }

    @Override
    public String getFieldReferenceName(Class<?> entityClass, Object idValue, Class<?> fieldClass, String fieldName) {
        try {
            String encode = bytesToHex(codec.getMapKeyEncoder().encode(idValue));
            return "redisson_live_object_field:{" + encode + "}:" + entityClass.getName() + ":" + fieldName;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable create name for '" + entityClass + "' and field:'" + fieldName + "' with id:" + idValue, ex);
        }
    }

    @Override
    public Object resolveId(String name) {
        String decode = name.substring(name.indexOf("{") + 1, name.indexOf("}"));
        
        ByteBuf b = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(decode)); 
        try {
            return codec.getMapKeyDecoder().decode(b, new State());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to decode [" + decode + "] into object", ex);
        } finally {
            b.release();
        }
    }

    private static String bytesToHex(ByteBuf bytes) {
        try {
            return ByteBufUtil.hexDump(bytes);
        } finally {
            bytes.release();
        }
    }

    @Override
    public String getIndexName(Class<?> entityClass, String fieldName) {
        return "redisson_live_object_index:{" + entityClass.getName() + "}:" + fieldName;
    }

}
