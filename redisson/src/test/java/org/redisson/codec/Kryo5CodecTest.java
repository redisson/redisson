package org.redisson.codec;

import io.netty.buffer.ByteBuf;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

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
        Assertions.assertThat(v11_1).containsOnly("123");

        LinkedHashMap<String, String> v12 = new LinkedHashMap<>();
        v12.put("1", "2");
        ByteBuf v2 = cc.getValueEncoder().encode(v12);
        LinkedHashMap<String, String> v12_1 = (LinkedHashMap<String, String>) cc.getValueDecoder().decode(v2, null);
        Assertions.assertThat(v12_1).containsEntry("1", "2");

        TestData v13 = new TestData("123");
        ByteBuf v3 = cc.getValueEncoder().encode(v13);
        TestData v13_3 = (TestData) cc.getValueDecoder().decode(v3, null);
        Assertions.assertThat(v13_3.getValue()).isEqualTo("123");

    }

}
