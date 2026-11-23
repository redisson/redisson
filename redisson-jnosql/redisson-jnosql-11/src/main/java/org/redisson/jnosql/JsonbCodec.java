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
package org.redisson.jnosql;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import jakarta.json.bind.Jsonb;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class JsonbCodec extends BaseCodec {

    private final Jsonb jsonb;
    private final Class<?> type;
    private final Encoder encoder;
    private final Decoder<Object> decoder;

    public JsonbCodec(Jsonb jsonb, Class<?> type) {
        this.jsonb = Objects.requireNonNull(jsonb, "jsonb is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.encoder = in -> {
            String json = jsonb.toJson(in);
            ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
            out.writeCharSequence(json, StandardCharsets.UTF_8);
            return out;
        };
        this.decoder = (buf, state) -> {
            String json = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
            return jsonb.fromJson(json, type);
        };
    }

    public JsonbCodec(ClassLoader classLoader, JsonbCodec codec) {
        this(codec.jsonb, codec.type);
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonbCodec that = (JsonbCodec) o;
        return jsonb.equals(that.jsonb) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonb, type);
    }

}
