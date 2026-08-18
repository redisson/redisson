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
package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;
import org.apache.fory.reflect.TypeRef;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.client.handler.State;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TypedJsonForyCodecTest extends RedisDockerTest {

    public static class Simple {

        private String value;

        public Simple() {
        }

        public Simple(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

    }

    public enum TestOperation {

        ADD {
            @Override
            public int apply(int value) {
                return value + 1;
            }
        };

        public abstract int apply(int value);
    }

    private final TypedJsonForyCodec valueCodec = new TypedJsonForyCodec(new TypeRef<Object>() { });

    private final TypedJsonForyCodec mapCodec =
            new TypedJsonForyCodec(null, new TypeRef<Map<String, List<String>>>() { });

    private final TypedJsonForyCodec stringCodec = new TypedJsonForyCodec(null, String.class);

    private final Simple value = new Simple("test");

    private final HashMap<String, List<String>> map = new HashMap<>();

    {
        map.put("foo", new ArrayList<>(Arrays.asList("bar")));
    }

    @Test
    public void testMap() {
        RMap<String, byte[]> map = redisson.getMap("anyMap", new TypedJsonForyCodec(String.class, byte[].class));
        map.put("2", new byte[]{1, 2, 3});
        assertThat(map.get("2")).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void testBucket() {
        RBucket<String> bucket = redisson.getBucket("anyMap", new TypedJsonForyCodec(String.class));
        bucket.set("2");
        assertThat(bucket.get()).isEqualTo("2");
    }

    @Test
    public void shouldDeserializeTheMapCorrectly() throws Exception {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(mapCodec.getForyJson().toJsonBytes(map));
        assertThat(mapCodec.getMapValueDecoder().decode(buf, new State()))
                .isInstanceOf(Map.class)
                .isEqualTo(map);
        buf.release();
    }

    @Test
    public void shouldSerializeValueCorrectly() throws Exception {
        ByteBuf buf = valueCodec.getValueEncoder().encode(value);
        assertThat(buf.toString(CharsetUtil.UTF_8)).isEqualTo("{\"value\":\"test\"}");
        buf.release();
    }

    @Test
    public void shouldSerializeTheMapCorrectly() throws Exception {
        ByteBuf buf = mapCodec.getMapValueEncoder().encode(map);
        assertThat(buf.toString(CharsetUtil.UTF_8)).isEqualTo("{\"foo\":[\"bar\"]}");
        buf.release();
    }

    @Test
    public void shouldDeserializeTheStringCorrectly() throws Exception {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(stringCodec.getForyJson().toJsonBytes("axk"));
        assertThat(stringCodec.getMapValueDecoder().decode(buf, new State()))
                .isInstanceOf(String.class)
                .isEqualTo("axk");
        buf.release();
    }

    @Test
    public void shouldSerializeTheStringCorrectly() throws Exception {
        ByteBuf buf = mapCodec.getMapValueEncoder().encode("foo");
        assertThat(buf.toString(CharsetUtil.UTF_8)).isEqualTo("\"foo\"");
        buf.release();
    }

    @Test
    public void shouldNotStoreTypeInformation() throws Exception {
        TypedJsonForyCodec codec = new TypedJsonForyCodec(Simple.class);

        ByteBuf buf = codec.getValueEncoder().encode(value);
        assertThat(buf.toString(CharsetUtil.UTF_8)).doesNotContain("@class");
        assertThat(((Simple) codec.getValueDecoder().decode(buf, new State())).getValue()).isEqualTo("test");
        buf.release();
    }

    @Test
    public void shouldSupportSeparateKeyAndValueTypes() throws Exception {
        TypedJsonForyCodec codec = new TypedJsonForyCodec(Long.class, Simple.class);

        ByteBuf key = codec.getMapKeyEncoder().encode(5L);
        assertThat(codec.getMapKeyDecoder().decode(key, new State())).isEqualTo(5L);
        key.release();

        ByteBuf mapValue = codec.getMapValueEncoder().encode(value);
        assertThat(((Simple) codec.getMapValueDecoder().decode(mapValue, new State())).getValue()).isEqualTo("test");
        mapValue.release();
    }

    @Test
    public void shouldSupportGenericValueTypes() throws Exception {
        TypedJsonForyCodec codec = new TypedJsonForyCodec(new TypeRef<List<Simple>>() { });

        ByteBuf buf = codec.getValueEncoder().encode(Arrays.asList(value, new Simple("test2")));
        @SuppressWarnings("unchecked")
        List<Simple> decoded = (List<Simple>) codec.getValueDecoder().decode(buf, new State());
        assertThat(decoded).hasSize(2);
        assertThat(decoded.get(0).getValue()).isEqualTo("test");
        assertThat(decoded.get(1).getValue()).isEqualTo("test2");
        buf.release();
    }

    @Test
    public void shouldReadUntypedValuesWhenNoTypeIsDefined() throws Exception {
        TypedJsonForyCodec codec = new TypedJsonForyCodec((Class<?>) null);

        ByteBuf buf = codec.getValueEncoder().encode(map);
        assertThat(codec.getValueDecoder().decode(buf, new State())).isEqualTo(map);
        buf.release();
    }

    @Test
    public void shouldSerializeAnEnumWithAClassBody() throws Exception {
        TypedJsonForyCodec codec = new TypedJsonForyCodec(TestOperation.class);

        ByteBuf buf = codec.getValueEncoder().encode(TestOperation.ADD);
        assertThat(buf.toString(CharsetUtil.UTF_8)).isEqualTo("\"ADD\"");
        assertThat(codec.getValueDecoder().decode(buf, new State())).isEqualTo(TestOperation.ADD);
        buf.release();
    }

    @Test
    public void shouldCopyWithClassLoader() throws IOException {
        ClassLoader classLoader = new ClassLoader(getClass().getClassLoader()) { };
        TypedJsonForyCodec original = new TypedJsonForyCodec(Simple.class);
        TypedJsonForyCodec copy = new TypedJsonForyCodec(classLoader, original);

        assertThat(copy.getClassLoader()).isSameAs(classLoader);

        ByteBuf buf = copy.getValueEncoder().encode(value);
        assertThat(((Simple) copy.getValueDecoder().decode(buf, new State())).getValue()).isEqualTo("test");
        buf.release();
    }
}
