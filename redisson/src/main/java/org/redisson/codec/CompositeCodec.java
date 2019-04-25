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
package org.redisson.codec;

import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CompositeCodec implements Codec {

    private final Codec mapKeyCodec;
    private final Codec mapValueCodec;
    private final Codec valueCodec;
    
    public CompositeCodec(Codec mapKeyCodec, Codec mapValueCodec) {
        this(mapKeyCodec, mapValueCodec, null);
    }
    
    public CompositeCodec(Codec mapKeyCodec, Codec mapValueCodec, Codec valueCodec) {
        super();
        this.mapKeyCodec = mapKeyCodec;
        this.mapValueCodec = mapValueCodec;
        this.valueCodec = valueCodec;
    }

    public CompositeCodec(ClassLoader classLoader, CompositeCodec codec) throws ReflectiveOperationException {
        super();
        this.mapKeyCodec = BaseCodec.copy(classLoader, codec.mapKeyCodec);
        this.mapValueCodec = BaseCodec.copy(classLoader, codec.mapValueCodec);
        this.valueCodec = BaseCodec.copy(classLoader, codec.valueCodec);
    }
    
    @Override
    public Decoder<Object> getMapValueDecoder() {
        return mapValueCodec.getMapValueDecoder();
    }

    @Override
    public Encoder getMapValueEncoder() {
        return mapValueCodec.getMapValueEncoder();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        return mapKeyCodec.getMapKeyDecoder();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        return mapKeyCodec.getMapKeyEncoder();
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return valueCodec.getValueDecoder();
    }

    @Override
    public Encoder getValueEncoder() {
        return valueCodec.getValueEncoder();
    }

    @Override
    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mapKeyCodec == null) ? 0 : mapKeyCodec.hashCode());
        result = prime * result + ((mapValueCodec == null) ? 0 : mapValueCodec.hashCode());
        result = prime * result + ((valueCodec == null) ? 0 : valueCodec.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CompositeCodec other = (CompositeCodec) obj;
        if (mapKeyCodec == null) {
            if (other.mapKeyCodec != null)
                return false;
        } else if (!mapKeyCodec.equals(other.mapKeyCodec))
            return false;
        if (mapValueCodec == null) {
            if (other.mapValueCodec != null)
                return false;
        } else if (!mapValueCodec.equals(other.mapValueCodec))
            return false;
        if (valueCodec == null) {
            if (other.valueCodec != null)
                return false;
        } else if (!valueCodec.equals(other.valueCodec))
            return false;
        return true;
    }

}
