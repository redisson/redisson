package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class Kryo5CodecTest {

    public static final class TestData {

        private String value;

        public TestData(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    public void test() throws IOException {
        Kryo5Codec cc = new Kryo5Codec();
        LinkedHashSet<String> v11 = new LinkedHashSet<>();
        v11.add("123");
        ByteBuf v1 = cc.getValueEncoder().encode(v11);
        LinkedHashSet<String> v11_1 = (LinkedHashSet<String>) cc.getValueDecoder().decode(v1, null);
        assertThat(v11_1).containsOnly("123");

        LinkedHashMap<String, String> v12 = new LinkedHashMap<>();
        v12.put("1", "2");
        ByteBuf v2 = cc.getValueEncoder().encode(v12);
        LinkedHashMap<String, String> v12_1 = (LinkedHashMap<String, String>) cc.getValueDecoder().decode(v2, null);
        assertThat(v12_1).containsEntry("1", "2");

        TestData v13 = new TestData("123");
        ByteBuf v3 = cc.getValueEncoder().encode(v13);
        TestData v13_3 = (TestData) cc.getValueDecoder().decode(v3, null);
        assertThat(v13_3.getValue()).isEqualTo("123");

    }

    public enum TestEnum {
        VAL1("A"),
        VAL2("B");

        private final String value;

        TestEnum(final String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }

    @Test
    public void testEnumMap() throws IOException {
        Kryo5Codec cc = new Kryo5Codec();
        Map<TestEnum, String> map = new EnumMap<>(TestEnum.class);
        map.put(TestEnum.VAL1, "abc");
        ByteBuf r = cc.getValueEncoder().encode(map);
        Map mm = (Map) cc.getValueDecoder().decode(r, null);
        assertThat(mm).isEqualTo(map);
    }

}
