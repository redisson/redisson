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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonForyCodecTest {

    private static final int DEFAULT_MAX_DEPTH_OVERFLOW = JsonForyCodec.DEFAULT_MAX_DEPTH + 10;

    private final JsonForyCodec codec = new JsonForyCodec();

    private Object roundTrip(Object value) throws IOException {
        return roundTrip(codec, value);
    }

    private Object roundTrip(JsonForyCodec codec, Object value) throws IOException {
        ByteBuf encoded = codec.getValueEncoder().encode(value);
        try {
            return codec.getValueDecoder().decode(encoded, null);
        } finally {
            encoded.release();
        }
    }

    private String encodeToString(Object value) throws IOException {
        ByteBuf encoded = codec.getValueEncoder().encode(value);
        try {
            return encoded.toString(StandardCharsets.UTF_8);
        } finally {
            encoded.release();
        }
    }

    private Object decode(String json) throws IOException {
        ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
        try {
            return codec.getValueDecoder().decode(buf, null);
        } finally {
            buf.release();
        }
    }

    @Nested
    class BasicSerializationTests {

        @Test
        void testStringSerDe() throws IOException {
            assertThat(roundTrip("Hello, Fory JSON!")).isEqualTo("Hello, Fory JSON!");
        }

        @Test
        void testIntegerSerDe() throws IOException {
            assertThat(((Number) roundTrip(42)).intValue()).isEqualTo(42);
        }

        @Test
        void testLongSerDe() throws IOException {
            assertThat(((Number) roundTrip(Long.MAX_VALUE)).longValue()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void testDoubleSerDe() throws IOException {
            assertThat(roundTrip(3.14159265359)).isEqualTo(3.14159265359);
        }

        @Test
        void testBooleanSerDe() throws IOException {
            assertThat(roundTrip(true)).isEqualTo(true);
        }

        @Test
        void testNullSerDe() throws IOException {
            assertThat(encodeToString(null)).isEqualTo("null");
            assertThat(roundTrip(null)).isNull();
        }

        @Test
        void testBigDecimalSerDe() throws IOException {
            BigDecimal original = new BigDecimal("123456789.1234567");

            assertThat(encodeToString(original)).isEqualTo("[\"java.math.BigDecimal\",123456789.1234567]");
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testUuidSerDe() throws IOException {
            UUID original = UUID.fromString("cb7e3de6-5801-45d4-bbfb-f8d8ed8e77fd");

            assertThat(encodeToString(original))
                    .isEqualTo("[\"java.util.UUID\",\"cb7e3de6-5801-45d4-bbfb-f8d8ed8e77fd\"]");
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testByteArraySerDe() throws IOException {
            assertThat(encodeToString(new byte[]{1, 2, 3})).isEqualTo("\"AQID\"");
        }
    }

    @Nested
    class CollectionTests {

        @Test
        void testListSerDe() throws IOException {
            List<String> original = Arrays.asList("one", "two", "three");

            assertThat(encodeToString(original))
                    .isEqualTo("[\"java.util.Arrays$ArrayList\",[\"one\",\"two\",\"three\"]]");
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testArrayListSerDe() throws IOException {
            List<String> original = new ArrayList<>(Arrays.asList("one", "two"));

            assertThat(encodeToString(original)).isEqualTo("[\"java.util.ArrayList\",[\"one\",\"two\"]]");
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @SuppressWarnings("unchecked")
        @Test
        void testSetSerDe() throws IOException {
            Set<Integer> original = new HashSet<>(Arrays.asList(1, 2, 3));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(Set.class);
            assertThat((Set<Object>) decoded).containsExactlyInAnyOrder(1, 2, 3);
        }

        @Test
        void testMapSerDe() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("name", "John");
            original.put("age", 30);
            original.put("active", true);

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"name\":\"John\",\"age\":30,\"active\":true}");

            Object decoded = roundTrip(original);
            assertThat(decoded).isInstanceOf(LinkedHashMap.class);
            Map<?, ?> decodedMap = (Map<?, ?>) decoded;
            assertThat(decodedMap.get("name")).isEqualTo("John");
            assertThat(((Number) decodedMap.get("age")).intValue()).isEqualTo(30);
            assertThat(decodedMap.get("active")).isEqualTo(true);
        }

        @SuppressWarnings("unchecked")
        @Test
        void testNestedCollectionSerDe() throws IOException {
            Map<String, List<Integer>> original = new HashMap<>();
            original.put("numbers", Arrays.asList(1, 2, 3));
            original.put("more", Arrays.asList(4, 5, 6));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(HashMap.class);
            Map<?, ?> decodedMap = (Map<?, ?>) decoded;
            assertThat((List<Object>) decodedMap.get("numbers")).containsExactly(1, 2, 3);
            assertThat((List<Object>) decodedMap.get("more")).containsExactly(4, 5, 6);
        }

        @Test
        void testEmptyListSerDe() throws IOException {
            Object decoded = roundTrip(Collections.emptyList());

            assertThat(decoded).isInstanceOf(List.class);
            assertThat((List<?>) decoded).isEmpty();
        }

        @Test
        void testEmptyMapSerDe() throws IOException {
            assertThat(encodeToString(Collections.emptyMap()))
                    .isEqualTo("{\"@class\":\"java.util.Collections$EmptyMap\"}");

            Object decoded = roundTrip(Collections.emptyMap());
            assertThat(decoded).isInstanceOf(Map.class);
            assertThat((Map<?, ?>) decoded).isEmpty();
        }

        @Test
        void testTreeMapKeepsItsType() throws IOException {
            TreeMap<String, Object> original = new TreeMap<>();
            original.put("b", "2");
            original.put("a", "1");

            assertThat(roundTrip(original)).isInstanceOf(TreeMap.class).isEqualTo(original);
        }

        @Test
        void testImmutableCollectionsAreRestoredAsMutableEquivalents() throws IOException {
            assertThat(roundTrip(Collections.singletonList("a")))
                    .isInstanceOf(List.class)
                    .isEqualTo(Collections.singletonList("a"));

            assertThat(roundTrip(Collections.unmodifiableList(new ArrayList<>(Arrays.asList("a", "b")))))
                    .isInstanceOf(List.class)
                    .isEqualTo(Arrays.asList("a", "b"));

            assertThat(roundTrip(Collections.unmodifiableMap(Collections.singletonMap("a", "b"))))
                    .isInstanceOf(Map.class)
                    .isEqualTo(Collections.singletonMap("a", "b"));

            assertThat(roundTrip(Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("a", "b")))))
                    .isInstanceOf(Set.class)
                    .isEqualTo(new LinkedHashSet<>(Arrays.asList("a", "b")));
        }

        @Test
        void testMapWithTypePropertyAsKey() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("@class", "not-a-class");

            assertThat(roundTrip(original)).isEqualTo(original);
        }
    }

    @Nested
    class POJOTests {

        @Test
        void testSimplePOJOSerDe() throws IOException {
            TestPerson original = new TestPerson("John Doe", 30);

            assertThat(encodeToString(original))
                    .startsWith("{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestPerson\",");

            Object decoded = roundTrip(original);
            assertThat(decoded).isInstanceOf(TestPerson.class);
            assertThat(((TestPerson) decoded).getName()).isEqualTo("John Doe");
            assertThat(((TestPerson) decoded).getAge()).isEqualTo(30);
        }

        @Test
        void testNestedPOJOSerDe() throws IOException {
            TestPersonWithAddress original =
                    new TestPersonWithAddress("Jane", new TestAddress("123 Main St", "New York"));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(TestPersonWithAddress.class);
            TestPersonWithAddress person = (TestPersonWithAddress) decoded;
            assertThat(person.getName()).isEqualTo("Jane");
            assertThat(person.getAddress().getStreet()).isEqualTo("123 Main St");
            assertThat(person.getAddress().getCity()).isEqualTo("New York");
        }

        @Test
        void testEmptyPOJOSerDe() throws IOException {
            assertThat(encodeToString(new TestEmpty()))
                    .isEqualTo("{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestEmpty\"}");
            assertThat(roundTrip(new TestEmpty())).isInstanceOf(TestEmpty.class);
        }

        @Test
        void testListOfPOJOSerDe() throws IOException {
            List<TestPerson> original = Arrays.asList(new TestPerson("Alice", 25), new TestPerson("Bob", 30));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(List.class);
            List<?> persons = (List<?>) decoded;
            assertThat(persons).hasSize(2);
            assertThat(((TestPerson) persons.get(0)).getName()).isEqualTo("Alice");
            assertThat(((TestPerson) persons.get(1)).getName()).isEqualTo("Bob");
        }

        @Test
        void testMapOfPOJOSerDe() throws IOException {
            Map<String, TestPerson> original = new HashMap<>();
            original.put("first", new TestPerson("Alice", 25));
            original.put("second", new TestPerson("Bob", 30));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(Map.class);
            Map<?, ?> persons = (Map<?, ?>) decoded;
            assertThat(persons).hasSize(2);
            assertThat(((TestPerson) persons.get("first")).getName()).isEqualTo("Alice");
            assertThat(((TestPerson) persons.get("second")).getName()).isEqualTo("Bob");
        }

        @Test
        void testDeeplyNestedPOJOSerDe() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("people", Arrays.asList(
                    Collections.singletonMap("person", new TestPerson("Alice", 25))));

            Object decoded = roundTrip(original);

            List<?> people = (List<?>) ((Map<?, ?>) decoded).get("people");
            Map<?, ?> entry = (Map<?, ?>) people.get(0);
            assertThat(((TestPerson) entry.get("person")).getName()).isEqualTo("Alice");
        }

        @Test
        void testUnknownPropertiesAreIgnored() throws IOException {
            Object decoded = decode("{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestPerson\","
                    + "\"name\":\"Test\",\"age\":30,\"unknownField\":\"ignored\"}");

            assertThat(decoded).isInstanceOf(TestPerson.class);
            assertThat(((TestPerson) decoded).getName()).isEqualTo("Test");
            assertThat(((TestPerson) decoded).getAge()).isEqualTo(30);
        }

        @Test
        void testUnknownClassFails() {
            assertThatThrownBy(() -> decode("{\"@class\":\"org.redisson.codec.Missing\",\"a\":1}"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("org.redisson.codec.Missing");
        }

        @Test
        void testGenericGetterIsStored() throws IOException {
            // org.redisson.MapWriterTask declares its getters this way. Apache Fory rejects such a class
            // when it reads the state from the bean properties, so the codec reads it from the fields
            TestGenericGetter original = new TestGenericGetter(Arrays.asList("a", "b"));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(TestGenericGetter.class);
            assertThat(((TestGenericGetter) decoded).getKeys()).containsExactly("a", "b");
        }

        @Test
        void testRenamedFieldIsStoredOnce() throws IOException {
            assertThat(encodeToString(new TestRenamedField("stored")))
                    .isEqualTo("{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestRenamedField\","
                            + "\"internalName\":\"stored\"}");

            Object decoded = roundTrip(new TestRenamedField("stored"));
            assertThat(((TestRenamedField) decoded).getPublicName()).isEqualTo("stored");
        }

        @Test
        void testDerivedPropertyIsNotStored() throws IOException {
            assertThat(encodeToString(new TestDerivedProperty("abc")))
                    .doesNotContain("length");

            Object decoded = roundTrip(new TestDerivedProperty("abc"));
            assertThat(((TestDerivedProperty) decoded).getStored()).isEqualTo("abc");
        }
    }

    @Nested
    class EnumTests {

        @Test
        void testEnumSerDe() throws IOException {
            assertThat(encodeToString(TestStatus.ACTIVE)).isEqualTo("\"ACTIVE\"");
            assertThat(roundTrip(TestStatus.ACTIVE)).isEqualTo("ACTIVE");
        }
    }

    @Nested
    class NestedTypeTests {

        @Test
        void testNestedNumbersKeepTheirType() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("int", 100);
            original.put("long", 10000000000L);
            original.put("smallLong", 5L);
            original.put("float", 100.0f);
            original.put("double", 100000.0);
            original.put("short", (short) 3);
            original.put("byte", (byte) 3);
            original.put("bigDecimal", new BigDecimal("1.5"));
            original.put("string", "testString");
            original.put("boolean", true);
            original.put("array", new ArrayList<Object>(Arrays.asList(1, 2.0, "three")));

            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testNaturalTypesAreStoredWithoutTypeInfo() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("s", "x");
            original.put("i", 1);
            original.put("d", 1.5);
            original.put("b", true);

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"s\":\"x\",\"i\":1,\"d\":1.5,\"b\":true}");
        }

        @Test
        void testNestedLongIsStoredWithTypeInfo() throws IOException {
            assertThat(encodeToString(Collections.singletonMap("v", 5L)))
                    .isEqualTo("{\"@class\":\"java.util.Collections$SingletonMap\",\"v\":[\"java.lang.Long\",5]}");
        }

        @Test
        void testNestedCollectionIsStoredWithTypeInfo() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("l", new ArrayList<>(Arrays.asList("x")));

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"l\":[\"java.util.ArrayList\",[\"x\"]]}");
        }

        @Test
        void testNestedPojoIsStoredWithTypeInfo() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("p", new TestPerson("Alice", 25));

            assertThat(encodeToString(original))
                    .contains("\"p\":{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestPerson\",");
        }

        @Test
        void testNestedByteArrayKeepsItsType() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("b", new byte[]{1, 2, 3});

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"b\":[\"[B\",\"AQID\"]}");

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(original);
            assertThat((byte[]) decoded.get("b")).containsExactly(1, 2, 3);
        }

        @Test
        void testNestedUuidKeepsItsType() throws IOException {
            UUID uuid = UUID.randomUUID();
            Map<?, ?> decoded = (Map<?, ?>) roundTrip(Collections.singletonMap("v", uuid));

            assertThat(decoded.get("v")).isEqualTo(uuid);
        }

        @Test
        void testDeeplyNestedContainersKeepTheirTypes() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("level1", new LinkedHashMap<>(Collections.singletonMap(
                    "level2", new ArrayList<>(Arrays.asList(
                            new TreeMap<>(Collections.singletonMap("n", 7L)))))));

            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testKeysAreAlwaysStrings() throws IOException {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put(5, "v");

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"5\":\"v\"}");
        }

        @Test
        void testKeysAreEscaped() throws IOException {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put("a\"b\\c\nd", 5L);

            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testMapKeyWithABrokenToStringIsRejected() {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put(new Object() {
                @Override
                public String toString() {
                    return null;
                }
            }, "v");

            assertThatThrownBy(() -> codec.getValueEncoder().encode(original))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("map key cannot be null");
        }

        @Test
        void testNullMapKeyIsRejected() {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put(null, new TestPerson("Alice", 25));

            assertThatThrownBy(() -> codec.getValueEncoder().encode(original))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("map key cannot be null");
        }

        @Test
        void testNullsInsideContainers() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", null);
            map.put("b", new TestPerson("Alice", 25));

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(map);
            assertThat(decoded.get("a")).isNull();
            assertThat(decoded.get("b")).isInstanceOf(TestPerson.class);

            List<Object> list = new ArrayList<>(Arrays.asList(null, new TestPerson("Bob", 30)));
            List<?> decodedList = (List<?>) roundTrip(list);
            assertThat(decodedList.get(0)).isNull();
            assertThat(decodedList.get(1)).isInstanceOf(TestPerson.class);
        }
    }

    @Nested
    class TypeFidelityTests {

        @Test
        void testSortedCollectionsAreRestored() throws IOException {
            TreeSet<Long> original = new TreeSet<>(Arrays.asList(1L, 2L, 3L));

            assertThat(roundTrip(original)).isInstanceOf(TreeSet.class).isEqualTo(original);
        }

        @Test
        void testSortedMapOfTypedValuesIsRestored() throws IOException {
            TreeMap<String, Object> original = new TreeMap<>();
            original.put("b", 2L);
            original.put("a", new TestPerson("Alice", 25));

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(original);
            assertThat(decoded).isInstanceOf(TreeMap.class);
            assertThat(decoded.get("b")).isEqualTo(2L);
            assertThat(((TestPerson) decoded.get("a")).getName()).isEqualTo("Alice");
        }

        @SuppressWarnings("unchecked")
        @Test
        void testPriorityQueueOfTypedValuesIsRestored() throws IOException {
            PriorityQueue<Long> original = new PriorityQueue<>(Arrays.asList(3L, 1L, 2L));

            Object decoded = roundTrip(original);

            assertThat(decoded).isInstanceOf(PriorityQueue.class);
            assertThat(new TreeSet<>((PriorityQueue<Long>) decoded)).containsExactly(1L, 2L, 3L);
        }

        @Test
        void testEnumWithBodyIsRestored() throws IOException {
            assertThat(roundTrip(TestOperation.ADD)).isEqualTo(TestOperation.ADD);
            assertThat(roundTrip(Collections.singletonMap("op", TestOperation.ADD)))
                    .isEqualTo(Collections.singletonMap("op", TestOperation.ADD));
        }

        @Test
        void testObjectArrayKeepsElementTypes() throws IOException {
            Object[] original = {1, 2L, "three", new TestPerson("Alice", 25)};

            Object[] decoded = (Object[]) roundTrip(original);

            assertThat(decoded[0]).isEqualTo(1);
            assertThat(decoded[1]).isEqualTo(2L);
            assertThat(decoded[2]).isEqualTo("three");
            assertThat(((TestPerson) decoded[3]).getName()).isEqualTo("Alice");
        }

        @Test
        void testNestedObjectArrayKeepsElementTypes() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("a", new Object[]{1, 2L});

            Object[] decoded = (Object[]) ((Map<?, ?>) roundTrip(original)).get("a");

            assertThat(decoded[0]).isEqualTo(1);
            assertThat(decoded[1]).isEqualTo(2L);
        }

        @Test
        void testPrimitiveArrayIsRestored() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("a", new int[]{1, 2, 3});

            assertThat((int[]) ((Map<?, ?>) roundTrip(original)).get("a")).containsExactly(1, 2, 3);
        }

        @Test
        void testRestrictedCodecReadsItsOwnOutput() throws IOException {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestPerson.class.getName())));

            List<Object> original = new ArrayList<>(Arrays.asList(1L, new TestPerson("Alice", 25)));

            List<?> decoded = (List<?>) roundTrip(restricted, original);
            assertThat(decoded.get(0)).isEqualTo(1L);
            assertThat(((TestPerson) decoded.get(1)).getName()).isEqualTo("Alice");
        }
    }

    @Nested
    class StabilityTests {

        private void assertStable(Object value) throws IOException {
            ByteBuf first = codec.getValueEncoder().encode(value);
            String firstJson = first.toString(StandardCharsets.UTF_8);
            Object decoded = codec.getValueDecoder().decode(first, null);
            first.release();

            ByteBuf second = codec.getValueEncoder().encode(decoded);
            String secondJson = second.toString(StandardCharsets.UTF_8);
            second.release();

            assertThat(secondJson).isEqualTo(firstJson);
        }

        @Test
        void testEncodingIsIdempotent() throws IOException {
            assertStable(new ArrayList<>(Arrays.asList("java.util.ArrayList", 5L)));
            assertStable(new LinkedHashMap<>(Collections.singletonMap("l", new ArrayList<>(Arrays.asList("x")))));
            assertStable(new HashMap<>(Collections.singletonMap("a", "1")));
            assertStable(new TreeMap<>(Collections.singletonMap("v", 1L)));
            assertStable(new TestPerson("John", 30));
            assertStable(new ArrayList<>(Arrays.asList(new TestPerson("A", 1), 2L, "three")));
        }

        @Test
        void testRepeatedRoundTripKeepsTheValue() throws IOException {
            Object value = new ArrayList<>(Arrays.asList("java.util.ArrayList", 5L));
            for (int i = 0; i < 5; i++) {
                value = roundTrip(value);
                assertThat(value).isEqualTo(Arrays.asList("java.util.ArrayList", 5L));
            }
        }

        @Test
        void testDecodedContainersAreNotForyInternalTypes() throws IOException {
            assertThat(roundTrip(new ArrayList<>(Arrays.asList("a"))).getClass().getName())
                    .isEqualTo("java.util.ArrayList");
            assertThat(roundTrip(new HashMap<>(Collections.singletonMap("a", "b"))).getClass().getName())
                    .startsWith("java.util.");
            assertThat(decode("{\"a\":1}").getClass().getName()).isEqualTo("java.util.LinkedHashMap");
            assertThat(decode("[1,2]").getClass().getName()).isEqualTo("java.util.ArrayList");
        }
    }

    @Nested
    class PrecisionTests {

        @Test
        void testNestedBigDecimalKeepsItsScale() throws IOException {
            BigDecimal original = new BigDecimal("1.10");

            List<?> decoded = (List<?>) roundTrip(new ArrayList<>(Arrays.asList(original)));

            assertThat(decoded.get(0)).isEqualTo(original);
            assertThat(((BigDecimal) decoded.get(0)).scale()).isEqualTo(2);
        }

        @Test
        void testNestedBigDecimalKeepsFullPrecision() throws IOException {
            BigDecimal original = new BigDecimal("0.1000000000000000000000005");

            assertThat(((List<?>) roundTrip(new ArrayList<>(Arrays.asList(original)))).get(0))
                    .isEqualTo(original);
            assertThat(((Map<?, ?>) roundTrip(Collections.singletonMap("v", original))).get("v"))
                    .isEqualTo(original);
        }

        @Test
        void testNestedBigIntegerKeepsFullPrecision() throws IOException {
            BigInteger original = new BigInteger("123456789012345678901234567890");

            assertThat(((List<?>) roundTrip(new ArrayList<>(Arrays.asList(original)))).get(0))
                    .isEqualTo(original);
        }

        @Test
        void testMalformedNumbersAreReportedAsIOException() {
            for (String malformed : Arrays.asList("1e", "1E", "1e+", ".", "-.", "..", "0x1E", "1.2.3",
                    "{\"a\":.}", "[1e]", "{\"a\":1e5x}")) {
                assertThatThrownBy(() -> decode(malformed))
                        .describedAs("decoding %s", malformed)
                        .isInstanceOf(IOException.class);
            }
        }

        @Test
        void testUntypedLargeIntegerIsRestored() throws IOException {
            assertThat(decode("123456789012345678901234567890"))
                    .isEqualTo(new BigInteger("123456789012345678901234567890"));
            assertThat(decode("2147483648")).isEqualTo(2147483648L);
            assertThat(decode("2147483647")).isEqualTo(2147483647);
            assertThat(decode("1.5")).isEqualTo(1.5d);
        }
    }

    @Nested
    class ArrayTypingTests {

        @Test
        void testFinalComponentArrayStoresNoMemberTypeInfo() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("a", new Long[]{1L, 2L});

            assertThat(encodeToString(original))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"a\":[\"[Ljava.lang.Long;\",[1,2]]}");

            assertThat((Long[]) ((Map<?, ?>) roundTrip(original)).get("a")).containsExactly(1L, 2L);
        }

        @Test
        void testNestedObjectArraysKeepMemberTypeInfo() throws IOException {
            Object[][] original = {{1, "a"}, {2L, null}};

            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("a", original);

            assertThat(encodeToString(holder)).contains("[\"[Ljava.lang.Object;\"").contains("[\"java.lang.Long\",2]");

            Object[][] decoded = (Object[][]) ((Map<?, ?>) roundTrip(holder)).get("a");
            assertThat(decoded[0][0]).isEqualTo(1);
            assertThat(decoded[0][1]).isEqualTo("a");
            assertThat(decoded[1][0]).isEqualTo(2L);
            assertThat(decoded[1][1]).isNull();
        }

        @Test
        void testFinalComponentNestedArraysStoreNoMemberTypeInfo() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("a", new String[][]{{"x"}});

            assertThat(encodeToString(holder))
                    .isEqualTo("{\"@class\":\"java.util.LinkedHashMap\",\"a\":[\"[[Ljava.lang.String;\",[[\"x\"]]]}");
        }

        @SuppressWarnings("unchecked")
        @Test
        void testCollectionWhoseFirstMemberIsAClassNameIsNotMisread() throws IOException {
            List<Object> original = new ArrayList<>(Arrays.asList(
                    "java.util.ArrayList", new ArrayList<>(Arrays.asList("a", "b"))));

            assertThat(roundTrip(original)).isEqualTo(original);

            Deque<Object> deque = new ArrayDeque<>(Arrays.asList("java.util.HashSet", "x"));
            assertThat(new ArrayList<>((Collection<Object>) roundTrip(deque)))
                    .containsExactly("java.util.HashSet", "x");

            assertThat(roundTrip(new ArrayList<>(Arrays.asList("java.util.UUID", "x"))))
                    .isEqualTo(Arrays.asList("java.util.UUID", "x"));
            assertThat(roundTrip(new ArrayList<>(Arrays.asList("java.lang.Runtime", "x"))))
                    .isEqualTo(Arrays.asList("java.lang.Runtime", "x"));
            assertThat(roundTrip(new Object[]{"java.util.ArrayList", "x"}))
                    .isEqualTo(new Object[]{"java.util.ArrayList", "x"});
        }

        @Test
        void testPrimitiveLeafArraysRoundTrip() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("short", new short[][]{{1, 2}});
            holder.put("char", new char[][]{{'a'}});
            holder.put("double", new double[][]{{Double.NaN, 1.5}});
            holder.put("byte", new byte[][]{{1, 2}, {3}});
            holder.put("int", new int[][]{{1}});
            holder.put("float", new float[][]{{Float.NaN}});

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(holder);
            assertThat((short[][]) decoded.get("short")).isDeepEqualTo(new short[][]{{1, 2}});
            assertThat((char[][]) decoded.get("char")).isDeepEqualTo(new char[][]{{'a'}});
            // NaN != NaN, so the non-finite members are checked on their own
            assertThat(((double[][]) decoded.get("double"))[0][0]).isNaN();
            assertThat(((double[][]) decoded.get("double"))[0][1]).isEqualTo(1.5);
            assertThat((byte[][]) decoded.get("byte")).isDeepEqualTo(new byte[][]{{1, 2}, {3}});
            assertThat((int[][]) decoded.get("int")).isDeepEqualTo(new int[][]{{1}});
            assertThat(((float[][]) decoded.get("float"))[0][0]).isNaN();
        }

        @Test
        void testNestedArraysOfAFinalComponentRoundTrip() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("s", new String[][]{{"a"}, null, {}});
            holder.put("l", new Long[][]{{1L, 2L}});
            holder.put("e", new String[][]{{}});

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(holder);
            assertThat((String[][]) decoded.get("s")).isDeepEqualTo(new String[][]{{"a"}, null, {}});
            assertThat((Long[][]) decoded.get("l")).isDeepEqualTo(new Long[][]{{1L, 2L}});
            assertThat((String[][]) decoded.get("e")).isDeepEqualTo(new String[][]{{}});
        }

        @Test
        void testNonFiniteMembersOfABoxedArrayRoundTrip() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("d", new Double[]{Double.NaN, Double.POSITIVE_INFINITY, 1.5d});
            holder.put("f", new Float[]{Float.NaN, 2.5f});

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(holder);
            assertThat((Double[]) decoded.get("d"))
                    .containsExactly(Double.NaN, Double.POSITIVE_INFINITY, 1.5d);
            assertThat((Float[]) decoded.get("f")).containsExactly(Float.NaN, 2.5f);
        }

        @Test
        void testMultiDimensionalUuidArrayRoundTrips() throws IOException {
            UUID uuid = UUID.randomUUID();
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("u", new UUID[][]{{uuid}});

            assertThat(((UUID[][]) ((Map<?, ?>) roundTrip(holder)).get("u"))[0][0]).isEqualTo(uuid);
        }

        @Test
        void testFinalComponentTypeArraysRoundTrip() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("final", new TestFinal[]{new TestFinal(1), new TestFinal(2)});
            holder.put("date", new java.time.LocalDate[]{java.time.LocalDate.of(2020, 1, 2)});
            holder.put("enum", new TestStatus[]{TestStatus.ACTIVE});

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(holder);
            assertThat(((TestFinal[]) decoded.get("final"))[0].getX()).isEqualTo(1);
            assertThat(((TestFinal[]) decoded.get("final"))[1].getX()).isEqualTo(2);
            assertThat((java.time.LocalDate[]) decoded.get("date"))
                    .containsExactly(java.time.LocalDate.of(2020, 1, 2));
            assertThat((TestStatus[]) decoded.get("enum")).containsExactly(TestStatus.ACTIVE);
        }

        @Test
        void testInterfaceComponentArrayIsEncodable() throws IOException {
            Map<String, Object> holder = new LinkedHashMap<>();
            holder.put("a", new java.io.Serializable[][]{{"x"}});

            assertThat(encodeToString(holder)).contains("[Ljava.io.Serializable;");

            java.io.Serializable[][] decoded =
                    (java.io.Serializable[][]) ((Map<?, ?>) roundTrip(holder)).get("a");
            assertThat(decoded[0][0]).isEqualTo("x");
        }

        @Test
        void testObjectComponentArrayStoresMemberTypeInfo() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("a", new Object[]{1, 2L});

            assertThat(encodeToString(original)).contains("[\"java.lang.Long\",2]");
        }
    }

    @Nested
    class DepthTests {

        @Test
        void testDeeplyNestedCollectionsRoundTrip() throws IOException {
            List<Object> root = new ArrayList<>();
            List<Object> current = root;
            for (int i = 0; i < 600; i++) {
                List<Object> next = new ArrayList<>();
                current.add(next);
                current = next;
            }

            assertThat(roundTrip(root)).isEqualTo(root);
        }

        @Test
        void testNestedDecodingIsNotQuadratic() throws IOException {
            long shallow = timeRoundTrip(60);
            long deep = timeRoundTrip(480);

            // eight times the depth and the size, so a linear decoder stays well below a 64x factor
            assertThat(deep).isLessThan(Math.max(shallow, 1) * 40);
        }

        private long timeRoundTrip(int depth) throws IOException {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> current = root;
            for (int i = 0; i < depth; i++) {
                Map<String, Object> next = new LinkedHashMap<>();
                current.put("a", "value");
                current.put("n", next);
                current = next;
            }
            ByteBuf encoded = codec.getValueEncoder().encode(root);
            byte[] bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            encoded.release();

            for (int i = 0; i < 50; i++) {
                decodeBytes(bytes);
            }
            long start = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                decodeBytes(bytes);
            }
            return System.nanoTime() - start;
        }

        private void decodeBytes(byte[] bytes) throws IOException {
            ByteBuf buf = Unpooled.wrappedBuffer(bytes);
            try {
                codec.getValueDecoder().decode(buf, null);
            } finally {
                buf.release();
            }
        }

        @Test
        void testAnArrayLookingLikeAWrapperDecodesInLinearTime() throws IOException {
            // every level starts with a resolvable class name but holds three elements, so the decoder
            // has to complete each level as a plain array without reading it a second time
            assertThat(timeCraftedDecode(20)).isLessThan(Math.max(timeCraftedDecode(10), 1) * 40);
        }

        private long timeCraftedDecode(int depth) throws IOException {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                json.append("[\"java.util.ArrayList\",");
            }
            json.append("1");
            for (int i = 0; i < depth; i++) {
                json.append(",\"x\"]");
            }
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);

            for (int i = 0; i < 20; i++) {
                decodeBytes(bytes);
            }
            long start = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                decodeBytes(bytes);
            }
            return System.nanoTime() - start;
        }

        @Test
        void testEveryNestingTheEncoderAcceptsCanBeDecoded() throws IOException {
            for (int depth : new int[]{800, 900, 998}) {
                List<Object> root = new ArrayList<>();
                List<Object> current = root;
                for (int i = 0; i < depth; i++) {
                    List<Object> next = new ArrayList<>();
                    current.add(next);
                    current = next;
                }
                assertThat(roundTrip(root)).describedAs("depth %s", depth).isEqualTo(root);
            }
        }

        @Test
        void testExcessiveNestingIsRejectedOnWrite() {
            List<Object> root = new ArrayList<>();
            List<Object> current = root;
            for (int i = 0; i < DEFAULT_MAX_DEPTH_OVERFLOW; i++) {
                List<Object> next = new ArrayList<>();
                current.add(next);
                current = next;
            }

            assertThatThrownBy(() -> codec.getValueEncoder().encode(root))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("max depth");
        }
    }

    @Nested
    class SecurityTests {

        @Test
        void testUnlistedJdkClassIsRejected() {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestPerson.class.getName())));

            ByteBuf buf = Unpooled.wrappedBuffer(
                    "{\"@class\":\"javax.swing.UIDefaults\",\"a\":\"b\"}".getBytes(StandardCharsets.UTF_8));
            try {
                assertThatThrownBy(() -> restricted.getValueDecoder().decode(buf, null))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("isn't allowed");
            } finally {
                buf.release();
            }
        }

        @Test
        void testArrayOfAllowedClassIsAccepted() throws IOException {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestPerson.class.getName())));

            Map<String, Object> original = new LinkedHashMap<>();
            original.put("a", new TestPerson[]{new TestPerson("Alice", 25)});

            TestPerson[] decoded = (TestPerson[]) ((Map<?, ?>) roundTrip(restricted, original)).get("a");
            assertThat(decoded[0].getName()).isEqualTo("Alice");
        }

        @Test
        void testRestrictedCodecReadsItsOwnNestedOutput() throws IOException {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestPerson.class.getName())));

            UUID uuid = UUID.randomUUID();
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("l", new ArrayList<>(Arrays.asList(1L, 2.5f, new BigDecimal("1.5"),
                    uuid, new TestPerson("Alice", 25))));

            List<?> decoded = (List<?>) ((Map<?, ?>) roundTrip(restricted, original)).get("l");
            assertThat(decoded.get(0)).isEqualTo(1L);
            assertThat(decoded.get(1)).isEqualTo(2.5f);
            assertThat(decoded.get(2)).isEqualTo(new BigDecimal("1.5"));
            assertThat(decoded.get(3)).isEqualTo(uuid);
            assertThat(((TestPerson) decoded.get(4)).getName()).isEqualTo("Alice");
        }
    }

    @Nested
    class MapKeyTests {

        @Test
        void testNonStringKeysUseToString() throws IOException {
            Map<Object, Object> uuidKeys = new LinkedHashMap<>();
            UUID key = UUID.randomUUID();
            uuidKeys.put(key, "v");
            assertThat(encodeToString(uuidKeys)).contains("\"" + key + "\":\"v\"");

            Map<Object, Object> dateKeys = new LinkedHashMap<>();
            Date date = new Date(1700000000000L);
            dateKeys.put(date, "v");
            assertThat(encodeToString(dateKeys)).contains("\"" + date + "\":\"v\"");
        }

        @Test
        void testEnumMapIsEncodable() throws IOException {
            EnumMap<TestStatus, String> original = new EnumMap<>(TestStatus.class);
            original.put(TestStatus.ACTIVE, "a");

            assertThat(roundTrip(original)).isEqualTo(Collections.singletonMap("ACTIVE", "a"));
        }

        @Test
        void testInvalidTemporalValueFailsWithIOException() {
            for (String malformed : Arrays.asList(
                    "[\"java.time.LocalDate\",\"2020-01-71\"]",
                    "[\"[Ljava.time.LocalDate;\",[\"2020-01-71\"]]",
                    "{\"@class\":\"java.util.LinkedHashMap\",\"k\":[\"java.time.LocalDate\",\"2020-01-71\"]}")) {
                assertThatThrownBy(() -> decode(malformed))
                        .describedAs("decoding %s", malformed)
                        .isInstanceOf(IOException.class);
            }
        }

        @Test
        void testTruncatedOrTrailingDataFailsWithIOException() {
            for (String malformed : Arrays.asList(
                    "[\"java.util.ArrayList\",[1,2]",
                    "{\"@class\":\"java.util.LinkedHashMap\",\"a\":1,\"b\":",
                    "{\"@class\":\"java.util.LinkedHashMap\",\"a\":1}garbage",
                    "\"a\" \"b\"",
                    "1 2",
                    "{\"a\":1}{\"b\":2}",
                    "[1,2",
                    "{\"a\":1")) {
                assertThatThrownBy(() -> decode(malformed))
                        .describedAs("decoding %s", malformed)
                        .isInstanceOf(IOException.class);
            }
        }

        @Test
        void testEncoderReportsAFailureAsIOException() {
            Map<Object, Object> unpairedSurrogate = new LinkedHashMap<>();
            unpairedSurrogate.put("a\uD800b", 1L);

            assertThatThrownBy(() -> codec.getValueEncoder().encode(unpairedSurrogate))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> codec.getValueEncoder().encode("a\uD800b"))
                    .isInstanceOf(IOException.class);
        }

        @Test
        void testMalformedWireDataFailsWithIOException() {
            for (String malformed : Arrays.asList(
                    "{\"@class\":\"java.util.concurrent.ConcurrentHashMap\",\"k\":null}",
                    "[\"java.util.LinkedHashMap\",\"scalar\"]",
                    "[\"java.util.Date\",\"not-a-date\"]",
                    "[\"java.util.UUID\",\"not-a-uuid\"]",
                    "[\"java.math.BigDecimal\",1e]",
                    "{\"@class\":\"org.redisson.codec.JsonForyCodecTest$TestPerson\",\"age\":\"x\"}")) {
                assertThatThrownBy(() -> decode(malformed))
                        .describedAs("decoding %s", malformed)
                        .isInstanceOf(IOException.class);
            }
        }

        @Test
        void testNullValueOfConcurrentMapFailsWithIOException() {
            ByteBuf buf = Unpooled.wrappedBuffer(
                    "{\"@class\":\"java.util.concurrent.ConcurrentHashMap\",\"k\":null}"
                            .getBytes(StandardCharsets.UTF_8));
            try {
                assertThatThrownBy(() -> codec.getValueDecoder().decode(buf, null))
                        .isInstanceOf(IOException.class);
            } finally {
                buf.release();
            }
        }
    }

    @Nested
    class NonFiniteAndSurrogateTests {

        @Test
        void testNestedNonFiniteDoublesKeepTheirType() throws IOException {
            List<Object> original = new ArrayList<>(Arrays.asList(
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.5d));

            List<?> decoded = (List<?>) roundTrip(original);

            assertThat(decoded.get(0)).isEqualTo(Double.NaN);
            assertThat(decoded.get(1)).isEqualTo(Double.POSITIVE_INFINITY);
            assertThat(decoded.get(2)).isEqualTo(Double.NEGATIVE_INFINITY);
            assertThat(decoded.get(3)).isEqualTo(1.5d);
        }

        @Test
        void testNonFiniteDoublesInAMapKeepTheirType() throws IOException {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("nan", Double.NaN);
            original.put("inf", Double.POSITIVE_INFINITY);

            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testNonFiniteDoublesDoNotCollapseSetMembers() throws IOException {
            Set<Object> original = new LinkedHashSet<>(Arrays.asList(Double.NaN, "NaN", 0.0d));

            assertThat((Set<?>) roundTrip(original)).hasSize(3);
        }

        @Test
        void testUnpairedSurrogateInAMapKeyIsNotSilentlyReplaced() {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put("a\uD800b", 1L);

            assertThatThrownBy(() -> codec.getValueEncoder().encode(original))
                    .isInstanceOf(Exception.class);
        }

        @Test
        void testPairedSurrogateInAMapKeyIsPreserved() throws IOException {
            Map<Object, Object> original = new LinkedHashMap<>();
            original.put("emoji \uD83C\uDF0D key", 1L);

            assertThat(roundTrip(original)).isEqualTo(original);
        }
    }

    @Nested
    class JacksonCompatibilityTests {

        private final JsonJackson3Codec jacksonCodec = new JsonJackson3Codec();

        private Object jacksonToFory(Object value) throws IOException {
            ByteBuf encoded = jacksonCodec.getValueEncoder().encode(value);
            try {
                return codec.getValueDecoder().decode(encoded, null);
            } finally {
                encoded.release();
            }
        }

        private Object foryToJackson(Object value) throws IOException {
            ByteBuf encoded = codec.getValueEncoder().encode(value);
            try {
                return jacksonCodec.getValueDecoder().decode(encoded, null);
            } finally {
                encoded.release();
            }
        }

        @Test
        void testPojoWrittenByJacksonIsReadableByFory() throws IOException {
            Object decoded = jacksonToFory(new TestPerson("John", 30));

            assertThat(decoded).isInstanceOf(TestPerson.class);
            assertThat(((TestPerson) decoded).getName()).isEqualTo("John");
        }

        @Test
        void testPojoWrittenByForyIsReadableByJackson() throws IOException {
            Object decoded = foryToJackson(new TestPerson("John", 30));

            assertThat(decoded).isInstanceOf(TestPerson.class);
            assertThat(((TestPerson) decoded).getAge()).isEqualTo(30);
        }

        @Test
        void testCollectionsAreInterchangeable() throws IOException {
            assertThat(jacksonToFory(new ArrayList<>(Arrays.asList("a", "b")))).isEqualTo(Arrays.asList("a", "b"));
            assertThat(foryToJackson(new ArrayList<>(Arrays.asList("a", "b")))).isEqualTo(Arrays.asList("a", "b"));
        }

        @Test
        void testMapsAreInterchangeable() throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("person", new TestPerson("Alice", 25));

            Map<?, ?> fromJackson = (Map<?, ?>) jacksonToFory(map);
            assertThat(((TestPerson) fromJackson.get("person")).getName()).isEqualTo("Alice");

            Map<?, ?> fromFory = (Map<?, ?>) foryToJackson(map);
            assertThat(((TestPerson) fromFory.get("person")).getName()).isEqualTo("Alice");
        }

        @Test
        void testMixedValueMapIsInterchangeable() throws IOException {
            Map<String, Object> original = new HashMap<>();
            original.put("double", 100000.0);
            original.put("float", 100.0f);
            original.put("int", 100);
            original.put("long", 10000000000L);
            original.put("boolt", true);
            original.put("boolf", false);
            original.put("string", "testString");
            original.put("array", new ArrayList<Object>(Arrays.asList(1, 2.0, "adsfasdfsdf")));

            assertThat(jacksonToFory(original)).isEqualTo(original);
            assertThat(foryToJackson(original)).isEqualTo(original);
        }

        @Test
        void testScalarsAreInterchangeable() throws IOException {
            assertThat(jacksonToFory("hello")).isEqualTo("hello");
            assertThat(foryToJackson("hello")).isEqualTo("hello");
            assertThat(jacksonToFory(true)).isEqualTo(true);
            assertThat(foryToJackson(true)).isEqualTo(true);
            assertThat(foryToJackson(new BigDecimal("1.5"))).isEqualTo(new BigDecimal("1.5"));
            assertThat(jacksonToFory(new BigDecimal("1.5"))).isEqualTo(new BigDecimal("1.5"));
            UUID uuid = UUID.randomUUID();
            assertThat(foryToJackson(uuid)).isEqualTo(uuid);
            assertThat(jacksonToFory(uuid)).isEqualTo(uuid);
        }
    }

    @Nested
    class AllowedClassesTests {

        @Test
        void testAllowedClassIsDecoded() throws IOException {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestPerson.class.getName())));

            Object decoded = roundTrip(restricted, new TestPerson("John", 30));

            assertThat(decoded).isInstanceOf(TestPerson.class);
        }

        @Test
        void testForbiddenClassIsRejected() throws IOException {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestAddress.class.getName())));

            ByteBuf encoded = codec.getValueEncoder().encode(new TestPerson("John", 30));
            try {
                assertThatThrownBy(() -> restricted.getValueDecoder().decode(encoded, null))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("isn't allowed");
            } finally {
                encoded.release();
            }
        }
    }

    @Nested
    class ClassLoaderTests {

        @Test
        void testCustomClassLoader() {
            ClassLoader customLoader = new ClassLoader(getClass().getClassLoader()) { };
            JsonForyCodec customCodec = new JsonForyCodec(customLoader);

            assertThat(customCodec.getClassLoader()).isSameAs(customLoader);
        }

        @Test
        void testCopyWithClassLoader() throws IOException {
            ClassLoader customLoader = new ClassLoader(getClass().getClassLoader()) { };
            JsonForyCodec copiedCodec = new JsonForyCodec(customLoader, codec);

            assertThat(copiedCodec.getClassLoader()).isSameAs(customLoader);
            assertThat(copiedCodec.getForyJson()).isNotSameAs(codec.getForyJson());
            assertThat(roundTrip(copiedCodec, new TestPerson("John", 30))).isInstanceOf(TestPerson.class);
        }

        @Test
        void testCopyKeepsAllowedClasses() throws Exception {
            JsonForyCodec restricted = new JsonForyCodec(
                    new HashSet<>(Arrays.asList(TestAddress.class.getName())));
            JsonForyCodec copy = new JsonForyCodec(getClass().getClassLoader(), restricted);

            ByteBuf encoded = codec.getValueEncoder().encode(new TestPerson("John", 30));
            try {
                assertThatThrownBy(() -> copy.getValueDecoder().decode(encoded, null))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("isn't allowed");
            } finally {
                encoded.release();
            }
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void testEmptyString() throws IOException {
            assertThat(roundTrip("")).isEqualTo("");
        }

        @Test
        void testUnicodeCharacters() throws IOException {
            String original = "Hello, 世界! 🌍";
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testSpecialCharacters() throws IOException {
            String original = "Hello\n\t\"World\"\\";
            assertThat(roundTrip(original)).isEqualTo(original);
        }

        @Test
        void testLargeNumbers() throws IOException {
            Map<String, Object> original = new HashMap<>();
            original.put("bigLong", Long.MAX_VALUE);
            original.put("bigDouble", Double.MAX_VALUE);

            Map<?, ?> decoded = (Map<?, ?>) roundTrip(original);

            assertThat(((Number) decoded.get("bigLong")).longValue()).isEqualTo(Long.MAX_VALUE);
            assertThat(((Number) decoded.get("bigDouble")).doubleValue()).isEqualTo(Double.MAX_VALUE);
        }

        @Test
        void testWhitespaceAroundTypeProperty() throws IOException {
            Object decoded = decode("{ \"@class\" : \"org.redisson.codec.JsonForyCodecTest$TestPerson\" ,"
                    + " \"name\" : \"Test\" }");

            assertThat(decoded).isInstanceOf(TestPerson.class);
            assertThat(((TestPerson) decoded).getName()).isEqualTo("Test");
        }

        @Test
        void testArrayStartingWithANaturalTypeNameIsNotATypeWrapper() throws IOException {
            assertThat(decode("[\"java.lang.String\",\"x\"]"))
                    .isEqualTo(Arrays.asList("java.lang.String", "x"));
        }

        @Test
        void testUntypedJsonIsStillReadable() throws IOException {
            assertThat(decode("{\"a\":1}")).isEqualTo(Collections.singletonMap("a", 1));
            assertThat(decode("[\"a\",\"b\"]")).isEqualTo(Arrays.asList("a", "b"));
            assertThat(decode("\"plain\"")).isEqualTo("plain");
            assertThat(decode("null")).isNull();
        }

        @Test
        void testDeepNesting() throws IOException {
            Map<String, Object> root = new HashMap<>();
            Map<String, Object> current = root;
            for (int i = 0; i < 100; i++) {
                Map<String, Object> next = new HashMap<>();
                current.put("next", next);
                current = next;
            }

            assertThat(roundTrip(root)).isInstanceOf(Map.class);
        }

        @Test
        void testEncoderReleasesBufferOnFailure() {
            List<Object> selfReferencing = new ArrayList<>();
            selfReferencing.add(selfReferencing);

            assertThatThrownBy(() -> codec.getValueEncoder().encode(selfReferencing))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    class ConcurrencyTests {

        @Test
        void testConcurrentSerDe() throws Exception {
            int threads = 8;
            int iterations = 500;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

            for (int t = 0; t < threads; t++) {
                int id = t;
                new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            TestPerson person = new TestPerson("person-" + id, i);
                            Object decoded = roundTrip(person);
                            if (!(decoded instanceof TestPerson)
                                    || ((TestPerson) decoded).getAge() != i
                                    || !((TestPerson) decoded).getName().equals("person-" + id)) {
                                failures.add(new AssertionError("Unexpected value " + decoded));
                            }
                        }
                    } catch (Throwable e) {
                        failures.add(e);
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            start.countDown();
            done.await();

            assertThat(failures).isEmpty();
        }
    }

    // Test helper classes

    public static class TestPerson {

        private String name;
        private int age;

        public TestPerson() {
        }

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class TestAddress {

        private String street;
        private String city;

        public TestAddress() {
        }

        public TestAddress(String street, String city) {
            this.street = street;
            this.city = city;
        }

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }
    }

    public static class TestPersonWithAddress {

        private String name;
        private TestAddress address;

        public TestPersonWithAddress() {
        }

        public TestPersonWithAddress(String name, TestAddress address) {
            this.name = name;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public TestAddress getAddress() {
            return address;
        }

        public void setAddress(TestAddress address) {
            this.address = address;
        }
    }

    /**
     * A class whose getter declares a different generic type than the field behind it, the shape of
     * {@code org.redisson.MapWriterTask}.
     */
    public static class TestGenericGetter {

        private Collection<?> keys = Collections.emptyList();

        public TestGenericGetter() {
        }

        public TestGenericGetter(Collection<?> keys) {
            this.keys = keys;
        }

        @SuppressWarnings("unchecked")
        public <V> Collection<V> getKeys() {
            return (Collection<V>) keys;
        }

    }

    public static class TestRenamedField {

        private String internalName;

        public TestRenamedField() {
        }

        public TestRenamedField(String internalName) {
            this.internalName = internalName;
        }

        public String getPublicName() {
            return internalName;
        }

        public void setPublicName(String publicName) {
            this.internalName = publicName;
        }

    }

    public static class TestDerivedProperty {

        private String stored;

        public TestDerivedProperty() {
        }

        public TestDerivedProperty(String stored) {
            this.stored = stored;
        }

        public String getStored() {
            return stored;
        }

        public void setStored(String stored) {
            this.stored = stored;
        }

        public int getLength() {
            if (stored == null) {
                return -1;
            }
            return stored.length();
        }

    }

    public static class TestEmpty {
    }

    public static final class TestFinal {

        private int x;

        public TestFinal() {
        }

        public TestFinal(int x) {
            this.x = x;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
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

    public enum TestStatus {
        ACTIVE,
        INACTIVE,
        PENDING
    }
}