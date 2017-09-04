package org.redisson.client.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.redisson.BaseTest;
import org.redisson.api.RMap;
import org.redisson.client.handler.State;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.CharsetUtil;

public class JsonJacksonMapValueCodecTest extends BaseTest {

    private final JsonJacksonMapCodec mapCodec = new JsonJacksonMapCodec(null, new TypeReference<Map<String, List<String>>>() {
    });

    private final JsonJacksonMapCodec stringCodec = new JsonJacksonMapCodec(null, String.class);

    private HashMap<String, List<String>> map = new HashMap<String, List<String>>();

    {
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));
    }

    @Test
    public void testMap() {
        RMap<String, byte[]> map = redisson.getMap("anyMap", new JsonJacksonMapCodec(String.class, byte[].class));
        map.put(String.valueOf("2"), new byte[]{1, 2, 3});
        assertThat(map.get("2")).isEqualTo(new byte[] {1, 2, 3});
    }
    
    @Test
    public void shouldDeserializeTheMapCorrectly() throws Exception {
        ByteBuf buf = new PooledByteBufAllocator(true).buffer();
        buf.writeBytes(new ObjectMapper().writeValueAsBytes(map));
        assertThat(mapCodec.getMapValueDecoder().decode(buf, new State(false)))
                .isInstanceOf(Map.class)
                .isEqualTo(map);
    }

    @Test
    public void shouldSerializeTheMapCorrectly() throws Exception {
        assertThat(mapCodec.getMapValueEncoder().encode(map).toString(CharsetUtil.UTF_8))
                .isEqualTo("{\"foo\":[\"bar\"]}");
    }

    @Test
    public void shouldDeserializeTheStringCorrectly() throws Exception {
        ByteBuf buf = new PooledByteBufAllocator(true).buffer();
        buf.writeBytes(new ObjectMapper().writeValueAsBytes("axk"));
        assertThat(stringCodec.getMapValueDecoder().decode(buf, new State(false)))
                .isInstanceOf(String.class)
                .isEqualTo("axk");
    }

    @Test
    public void shouldSerializeTheStringCorrectly() throws Exception {
        assertThat(mapCodec.getMapValueEncoder().encode("foo").toString(CharsetUtil.UTF_8))
                .isEqualTo("\"foo\"");
    }
}