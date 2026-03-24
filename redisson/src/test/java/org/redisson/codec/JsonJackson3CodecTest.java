/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class JsonJackson3CodecTest {

    private JsonJackson3Codec codec = new JsonJackson3Codec();

    @Nested
    class BasicSerializationTests {

        @Test
        void testStringSerDe() throws IOException {
            String original = "Hello, Jackson 3!";

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testIntegerSerDe() throws IOException {
            Integer original = 42;

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testLongSerDe() throws IOException {
            Long original = Long.MAX_VALUE;

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(((Number) decoded).longValue()).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testDoubleSerDe() throws IOException {
            Double original = 3.14159265359;

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testBooleanSerDe() throws IOException {
            Boolean original = true;

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testNullSerDe() throws IOException {
            ByteBuf encoded = codec.getValueEncoder().encode(null);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isNull();
            encoded.release();
        }

        @Test
        void testBigDecimalSerDe() throws IOException {
            BigDecimal original = new BigDecimal("123456789.1234567");

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(new BigDecimal(decoded.toString())).isEqualByComparingTo(original);
            encoded.release();
        }
    }

    @Nested
    class CollectionTests {

        @Test
        void testListSerDe() throws IOException {
            List<String> original = Arrays.asList("one", "two", "three");

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testSetSerDe() throws IOException {
            Set<Integer> original = new HashSet<>(Arrays.asList(1, 2, 3));

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            System.out.println(encoded.toString(StandardCharsets.UTF_8));

            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat((Set) decoded).containsExactlyInAnyOrder(1, 2, 3);
            encoded.release();
        }

        @Test
        void testMapSerDe() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("name", "John");
            original.put("age", 30);
            original.put("active", true);

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(Map.class);
            Map<?, ?> decodedMap = (Map<?, ?>) decoded;
            assertThat(decodedMap.get("name")).isEqualTo("John");
            assertThat(decodedMap.get("age")).isEqualTo(30);
            assertThat(decodedMap.get("active")).isEqualTo(true);
            encoded.release();
        }

        @Test
        void testNestedCollectionSerDe() throws IOException {
            Map<String, List<Integer>> original = new HashMap<>();
            original.put("numbers", Arrays.asList(1, 2, 3));
            original.put("more", Arrays.asList(4, 5, 6));

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, List<Integer>> decodedMap = (Map<String, List<Integer>>) decoded;
            assertThat(decodedMap.get("numbers")).containsExactly(1, 2, 3);
            assertThat(decodedMap.get("more")).containsExactly(4, 5, 6);
            encoded.release();
        }

        @Test
        void testEmptyListSerDe() throws IOException {
            List<String> original = Collections.emptyList();

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(List.class);
            assertThat((List<?>) decoded).isEmpty();
            encoded.release();
        }

        @Test
        void testEmptyMapSerDe() throws IOException {
            Map<String, Object> original = Collections.emptyMap();

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) decoded).isEmpty();
            encoded.release();
        }
    }

    @Nested
    class POJOTests {

        @Test
        void testSimplePOJOSerDe() throws IOException {
            TestPerson original = new TestPerson("John Doe", 30);

            // Use typed codec for POJO
            JsonJackson3Codec typedCodec = new JsonJackson3Codec();

            ByteBuf encoded = typedCodec.getValueEncoder().encode(original);
            String s = encoded.toString(StandardCharsets.UTF_8);
            System.out.println(s);
            Object decoded = typedCodec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(TestPerson.class);
            TestPerson person = (TestPerson) decoded;
            assertThat(person.getName()).isEqualTo("John Doe");
            assertThat(person.getAge()).isEqualTo(30);
            encoded.release();
        }

        @Test
        void testNestedPOJOSerDe() throws IOException {
            TestAddress address = new TestAddress("123 Main St", "New York");
            TestPersonWithAddress original = new TestPersonWithAddress("Jane", address);

            JsonJackson3Codec typedCodec = new JsonJackson3Codec();

            ByteBuf encoded = typedCodec.getValueEncoder().encode(original);
            Object decoded = typedCodec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(TestPersonWithAddress.class);
            TestPersonWithAddress person = (TestPersonWithAddress) decoded;
            assertThat(person.getName()).isEqualTo("Jane");
            assertThat(person.getAddress().getStreet()).isEqualTo("123 Main St");
            assertThat(person.getAddress().getCity()).isEqualTo("New York");
            encoded.release();
        }

    }

    @Nested
    class TypeReferenceTests {

        @Test
        void testTypeReferenceSerDe() throws IOException {
            List<TestPerson> original = Arrays.asList(
                    new TestPerson("Alice", 25),
                    new TestPerson("Bob", 30)
            );

            JsonJackson3Codec typedCodec = new JsonJackson3Codec();

            ByteBuf encoded = typedCodec.getValueEncoder().encode(original);
            Object decoded = typedCodec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<TestPerson> persons = (List<TestPerson>) decoded;
            assertThat(persons).hasSize(2);
            assertThat(persons.get(0).getName()).isEqualTo("Alice");
            assertThat(persons.get(1).getName()).isEqualTo("Bob");
            encoded.release();
        }

        @Test
        void testMapTypeReferenceSerDe() throws IOException {
            Map<String, TestPerson> original = new HashMap<>();
            original.put("first", new TestPerson("Alice", 25));
            original.put("second", new TestPerson("Bob", 30));

            JsonJackson3Codec typedCodec = new JsonJackson3Codec();

            ByteBuf encoded = typedCodec.getValueEncoder().encode(original);
            System.out.println(encoded.toString(StandardCharsets.UTF_8));
            Object decoded = typedCodec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, TestPerson> persons = (Map<String, TestPerson>) decoded;
            assertThat(persons).hasSize(2);
            assertThat(persons.get("first").getName()).isEqualTo("Alice");
            assertThat(persons.get("second").getName()).isEqualTo("Bob");
            encoded.release();
        }
    }

    @Nested
    class EnumTests {

        @Test
        void testEnumSerDe() throws IOException {
            TestStatus original = TestStatus.ACTIVE;

            ByteBuf encoded = codec.getValueEncoder().encode(original);

            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            String json = new String(bytes);

            // With WRITE_ENUMS_USING_TO_STRING enabled
            assertThat(json).contains("ACTIVE");
            encoded.release();
        }
    }

    @Nested
    class CustomMapperTests {

        @Test
        void testCustomMapper() throws IOException {
            ObjectMapper customMapper = JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .build();

            JsonJackson3Codec customCodec = new JsonJackson3Codec(customMapper);

            Map<String, Object> original = new LinkedHashMap<>();
            original.put("key", "value");

            ByteBuf encoded = customCodec.getValueEncoder().encode(original);

            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            String json = new String(bytes);

            // With INDENT_OUTPUT enabled, should have newlines
            assertThat(json).contains("\n");
            encoded.release();
        }

        @Test
        void testUnknownPropertiesHandling() throws IOException {
            // Create JSON with extra field
            String json = "{\"@class\":\"org.redisson.codec.JsonJackson3CodecTest$TestPerson\", \"name\":\"Test\",\"age\":30,\"unknownField\":\"ignored\"}";

            JsonJackson3Codec typedCodec = new JsonJackson3Codec();

            // Manually create ByteBuf from JSON string
            ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(json.getBytes());

            // Should not throw due to FAIL_ON_UNKNOWN_PROPERTIES being disabled
            Object decoded = typedCodec.getValueDecoder().decode(buf, null);

            assertThat(decoded).isInstanceOf(TestPerson.class);
            TestPerson person = (TestPerson) decoded;
            assertThat(person.getName()).isEqualTo("Test");
            assertThat(person.getAge()).isEqualTo(30);
            buf.release();
        }
    }

    @Nested
    class ClassLoaderTests {

        @Test
        void testCustomClassLoader() {
            ClassLoader customLoader = new ClassLoader(getClass().getClassLoader()) {};
            JsonJackson3Codec customCodec = new JsonJackson3Codec(customLoader);

            assertThat(customCodec.getClassLoader()).isSameAs(customLoader);
        }

        @Test
        void testCopyWithClassLoader() {
            ClassLoader customLoader = new ClassLoader(getClass().getClassLoader()) {};
            JsonJackson3Codec copiedCodec = new JsonJackson3Codec(customLoader, codec);

            assertThat(copiedCodec.getClassLoader()).isSameAs(customLoader);
            assertThat(copiedCodec.getObjectMapper()).isNotSameAs(codec.getObjectMapper());
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void testEmptyString() throws IOException {
            String original = "";

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo("");
            encoded.release();
        }

        @Test
        void testUnicodeCharacters() throws IOException {
            String original = "Hello, 世界! 🌍";

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testSpecialCharacters() throws IOException {
            String original = "Hello\n\t\"World\"\\";

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isEqualTo(original);
            encoded.release();
        }

        @Test
        void testLargeNumbers() throws IOException {
            Map<String, Object> original = new HashMap<>();
            original.put("bigLong", Long.MAX_VALUE);
            original.put("bigDouble", Double.MAX_VALUE);

            ByteBuf encoded = codec.getValueEncoder().encode(original);
            Object decoded = codec.getValueDecoder().decode(encoded, null);

            assertThat(decoded).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> decodedMap = (Map<String, Object>) decoded;
            assertThat(((Number) decodedMap.get("bigLong")).longValue()).isEqualTo(Long.MAX_VALUE);
            encoded.release();
        }

    }

    // Test helper classes

    public static class TestPerson {
        private String name;
        private int age;

        public TestPerson() {}

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    public static class TestAddress {
        private String street;
        private String city;

        public TestAddress() {}

        public TestAddress(String street, String city) {
            this.street = street;
            this.city = city;
        }

        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
    }

    public static class TestPersonWithAddress {
        private String name;
        private TestAddress address;

        public TestPersonWithAddress() {}

        public TestPersonWithAddress(String name, TestAddress address) {
            this.name = name;
            this.address = address;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public TestAddress getAddress() { return address; }
        public void setAddress(TestAddress address) { this.address = address; }
    }

    public enum TestStatus {
        ACTIVE,
        INACTIVE,
        PENDING
    }
}