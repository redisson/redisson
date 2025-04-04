package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void testAtomicObjects() throws IOException {
        Kryo5Codec cc = new Kryo5Codec();

        AtomicBoolean v1 = new AtomicBoolean(true);
        ByteBuf b1 = cc.getValueEncoder().encode(v1);
        AtomicBoolean v1_1 = (AtomicBoolean)cc.getValueDecoder().decode(b1, null);
        assertThat(v1_1).isTrue();

        AtomicInteger v2 = new AtomicInteger(3);
        ByteBuf b2 = cc.getValueEncoder().encode(v2);
        AtomicInteger v2_1 = (AtomicInteger)cc.getValueDecoder().decode(b2, null);
        assertThat(v2_1.get()).isEqualTo(3);

        AtomicLong v3 = new AtomicLong(3L);
        ByteBuf b3 = cc.getValueEncoder().encode(v3);
        AtomicLong v3_1 = (AtomicLong)cc.getValueDecoder().decode(b3, null);
        assertThat(v3_1.get()).isEqualTo(3L);

        AtomicReference<String> v4 = new AtomicReference<>("123");
        ByteBuf b4 = cc.getValueEncoder().encode(v4);
        AtomicReference<String> v4_1 = (AtomicReference<String>)cc.getValueDecoder().decode(b4, null);
        assertThat(v4_1.get()).isEqualTo("123");
    }

}
