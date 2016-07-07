package org.redisson.client.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Before;
import org.junit.Test;
import org.redisson.client.handler.State;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonJacksonMapValueCodecTest {

    private final JsonJacksonMapValueCodec<Map<String, List<String>>> mapCodec = new JsonJacksonMapValueCodec<Map<String, List<String>>>(new TypeReference<Map<String, List<String>>>() {
    });

    private final JsonJacksonMapValueCodec<String> stringCodec = new JsonJacksonMapValueCodec<String>(String.class);

    private HashMap<String, List<String>> map;

    @Before
    public void setUp() throws Exception {
        map = new HashMap<String, List<String>>();
        map.put("foo", new ArrayList<String>(Arrays.asList("bar")));
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
        assertThat(new String(mapCodec.getMapValueEncoder().encode(map), "UTF-8"))
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
        assertThat(new String(stringCodec.getMapValueEncoder().encode("foo"), "UTF-8"))
                .isEqualTo("\"foo\"");
    }
}