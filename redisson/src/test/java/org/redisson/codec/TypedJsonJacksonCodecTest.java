package org.redisson.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.api.RBucket;
import org.redisson.api.RMap;
import org.redisson.client.handler.State;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.CharsetUtil;

public class TypedJsonJacksonCodecTest extends BaseTest {

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
    
    private final TypedJsonJacksonCodec valueCodec = new TypedJsonJacksonCodec(new TypeReference<Object>() {});
    
    private final TypedJsonJacksonCodec mapCodec = new TypedJsonJacksonCodec(null, new TypeReference<Map<String, List<String>>>() {});

    private final TypedJsonJacksonCodec stringCodec = new TypedJsonJacksonCodec(null, String.class);

    private Simple value = new Simple("test");
    
    private HashMap<String, List<String>> map = new HashMap<String, List<String>>();

    {
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));
    }

    @Test
    public void testMap() {
        RMap<String, byte[]> map = redisson.getMap("anyMap", new TypedJsonJacksonCodec(String.class, byte[].class));
        map.put(String.valueOf("2"), new byte[]{1, 2, 3});
        assertThat(map.get("2")).isEqualTo(new byte[] {1, 2, 3});
    }
    
    @Test
    public void testBucket() {
        RBucket<String> bucket = redisson.getBucket("anyMap", new TypedJsonJacksonCodec(String.class));
        bucket.set(String.valueOf("2"));
        assertThat(bucket.get()).isEqualTo("2");
    }
    
    @Test
    public void shouldDeserializeTheMapCorrectly() throws Exception {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(new ObjectMapper().writeValueAsBytes(map));
        assertThat(mapCodec.getMapValueDecoder().decode(buf, new State()))
                .isInstanceOf(Map.class)
                .isEqualTo(map);
        buf.release();
    }

    @Test
    public void shouldSerializeValueCorrectly() throws Exception {
        assertThat(valueCodec.getValueEncoder().encode(value).toString(CharsetUtil.UTF_8))
                .isEqualTo("{\"value\":\"test\"}");
    }
    
    @Test
    public void shouldSerializeTheMapCorrectly() throws Exception {
        assertThat(mapCodec.getMapValueEncoder().encode(map).toString(CharsetUtil.UTF_8))
                .isEqualTo("{\"foo\":[\"bar\"]}");
    }

    @Test
    public void shouldDeserializeTheStringCorrectly() throws Exception {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(new ObjectMapper().writeValueAsBytes("axk"));
        assertThat(stringCodec.getMapValueDecoder().decode(buf, new State()))
                .isInstanceOf(String.class)
                .isEqualTo("axk");
        buf.release();
    }

    @Test
    public void shouldSerializeTheStringCorrectly() throws Exception {
        assertThat(mapCodec.getMapValueEncoder().encode("foo").toString(CharsetUtil.UTF_8))
                .isEqualTo("\"foo\"");
    }
}