package org.redisson.codec;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.client.handler.State;

public class JsonJacksonCodecTest {

    static class Bean1599 {
       public int id;
       public Object obj;
    }
    
    @Test
    public void test() {
        Assertions.assertThrows(JsonMappingException.class, () -> {
            String JSON =
                    "{'id': 124,\n" +
                            " 'obj':[ 'com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl',\n" +
                            "  {\n" +
                            "    'transletBytecodes' : [ 'AAIAZQ==' ],\n" +
                            "    'transletName' : 'a.b',\n" +
                            "    'outputProperties' : { }\n" +
                            "  }\n" +
                            " ]\n" +
                            "}";
            JSON = JSON.replace("'", "\"");

            JsonJacksonCodec codec = new JsonJacksonCodec();
            codec.getObjectMapper().readValue(JSON, Bean1599.class);
        });
    }

    @Test
    public void shouldSerializeAndDeserializeThrowable() throws JsonProcessingException {
        //given
        ObjectMapper objectMapper = JsonJacksonCodec.INSTANCE.getObjectMapper();
        //when
        String serialized = objectMapper.writeValueAsString(new RuntimeException("Example message"));
        RuntimeException deserialized = objectMapper.readValue(serialized, RuntimeException.class);
        //then
        Assertions.assertEquals("Example message", deserialized.getMessage());
    }

    @Test
    public void shouldNotOverrideProvidedObjectMapperProperties() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        objectMapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, false);
        JsonJacksonCodec codec = new JsonJacksonCodec(objectMapper);

        Assertions.assertTrue(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        Assertions.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

        Assertions.assertFalse(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
        Assertions.assertFalse(codec.getObjectMapper().getDeserializationConfig().isEnabled(DeserializationFeature.UNWRAP_ROOT_VALUE));
    }

    @Test
    public void shouldRoundTripUuidViaUntypedDecoder() throws IOException {
        UUID uuid = UUID.randomUUID();
        JsonJacksonCodec codec = new JsonJacksonCodec();

        ByteBuf encoded = codec.getValueEncoder().encode(uuid);
        try {
            Object decoded = codec.getValueDecoder().decode(encoded, new State());
            Assertions.assertEquals(uuid, decoded);
        } finally {
            encoded.release();
        }
    }

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
    }

    public static class TestAddress {
        private String city;

        public TestAddress() {
        }

        public TestAddress(String city) {
            this.city = city;
        }
    }

    public static final class TestSecret {
        private String value;

        public TestSecret() {
        }
    }

    public static final class TestTracked {
        static volatile boolean instantiated;

        private String value;

        public TestTracked() {
            instantiated = true;
        }
    }

    public static final class TestLoadFlag {
        static volatile boolean loaded;
    }

    public static final class TestNeverLoaded {
        static {
            TestLoadFlag.loaded = true;
        }

        private String value;

        public TestNeverLoaded() {
        }
    }

    public static class TestAnnotatedWrapper {
        @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS)
        public Object payload;

        public TestAnnotatedWrapper() {
        }
    }

    public enum TestColor {
        RED,
        GREEN
    }

    @Nested
    class AllowedClassesTests {

        private final Set<String> allowed = new HashSet<>(Arrays.asList(TestPerson.class.getName()));

        private Object roundTrip(JsonJacksonCodec decodeWith, Object value) throws IOException {
            ByteBuf encoded = JsonJacksonCodec.INSTANCE.getValueEncoder().encode(value);
            try {
                return decodeWith.getValueDecoder().decode(encoded, new State());
            } finally {
                encoded.release();
            }
        }

        @Test
        public void testAllowedClassIsDecoded() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            Object decoded = roundTrip(restricted, new TestPerson("John", 30));

            Assertions.assertInstanceOf(TestPerson.class, decoded);
            Assertions.assertEquals("John", ((TestPerson) decoded).getName());
        }

        @Test
        public void testForbiddenClassIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            JsonMappingException e = Assertions.assertThrows(JsonMappingException.class,
                    () -> roundTrip(restricted, new TestAddress("NY")));
            Assertions.assertTrue(e.getMessage().contains(TestAddress.class.getName()), e.getMessage());
        }

        @Test
        public void testForbiddenClassNestedInCollectionIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            Assertions.assertThrows(JsonMappingException.class,
                    () -> roundTrip(restricted, new ArrayList<>(Arrays.asList(new TestAddress("NY")))));
        }

        @Test
        public void testAllowedClassArrayIsDecoded() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            Object decoded = roundTrip(restricted, new TestPerson[]{new TestPerson("John", 30)});

            Assertions.assertInstanceOf(TestPerson[].class, decoded);
        }

        @Test
        public void testForbiddenClassArrayIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            Assertions.assertThrows(JsonMappingException.class,
                    () -> roundTrip(restricted, new TestAddress[]{new TestAddress("NY")}));
        }

        @Test
        public void testJdkClassesAreDecoded() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            Map<String, Object> value = new HashMap<>();
            value.put("list", new ArrayList<>(Arrays.asList("one", "two")));
            value.put("uuid", UUID.randomUUID());
            value.put("date", new Date(1000000L));
            value.put("decimal", new BigDecimal("1.5"));
            value.put("long", 42L);

            Assertions.assertEquals(value, roundTrip(restricted, value));
        }

        private Object decode(JsonJacksonCodec codec, String json) throws IOException {
            ByteBuf buf = Unpooled.wrappedBuffer(json.getBytes(StandardCharsets.UTF_8));
            try {
                return codec.getValueDecoder().decode(buf, new State());
            } finally {
                buf.release();
            }
        }

        @Test
        public void testForbiddenClassInGenericTypeIdIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            String json = "[\"java.util.ArrayList<" + TestSecret.class.getName() + ">\",[{\"value\":\"boom\"}]]";

            Assertions.assertThrows(JsonMappingException.class, () -> decode(restricted, json));
        }

        @Test
        public void testForbiddenClassInGenericMapTypeIdIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            String json = "{\"@class\":\"java.util.HashMap<java.lang.String," + TestSecret.class.getName()
                    + ">\",\"k\":{\"value\":\"boom\"}}";

            Assertions.assertThrows(JsonMappingException.class, () -> decode(restricted, json));
        }

        @Test
        public void testAllowedClassInGenericTypeIdIsDecoded() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(
                    new HashSet<>(Arrays.asList(TestSecret.class.getName())));

            String json = "[\"java.util.ArrayList<" + TestSecret.class.getName() + ">\",[{\"value\":\"boom\"}]]";

            Object decoded = decode(restricted, json);

            Assertions.assertInstanceOf(List.class, decoded);
            Assertions.assertInstanceOf(TestSecret.class, ((List<?>) decoded).get(0));
        }

        @Test
        public void testForbiddenEnumOfEnumMapIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            EnumMap<TestColor, Object> value = new EnumMap<>(TestColor.class);
            value.put(TestColor.RED, "v");

            Assertions.assertThrows(JsonMappingException.class, () -> roundTrip(restricted, value));
        }

        @Test
        public void testAllowedEnumOfEnumMapIsDecoded() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(
                    new HashSet<>(Arrays.asList(TestColor.class.getName())));

            EnumMap<TestColor, Object> value = new EnumMap<>(TestColor.class);
            value.put(TestColor.RED, "v");

            Assertions.assertEquals(value, roundTrip(restricted, value));
        }

        @Test
        public void testEmptyAllowedClassesAllowsAnyClass() throws IOException {
            Assertions.assertInstanceOf(TestAddress.class,
                    roundTrip(new JsonJacksonCodec(Collections.emptySet()), new TestAddress("NY")));
            Assertions.assertInstanceOf(TestAddress.class,
                    roundTrip(new JsonJacksonCodec((Set<String>) null), new TestAddress("NY")));
            Assertions.assertInstanceOf(TestAddress.class,
                    roundTrip(new JsonJacksonCodec(), new TestAddress("NY")));
        }

        @Test
        public void testClassLoaderConstructorKeepsAllowedClasses() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(getClass().getClassLoader(), allowed);

            Assertions.assertInstanceOf(TestPerson.class, roundTrip(restricted, new TestPerson("John", 30)));
            Assertions.assertThrows(JsonMappingException.class, () -> roundTrip(restricted, new TestAddress("NY")));
        }

        @Test
        public void testCopyKeepsAllowedClasses() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);
            JsonJacksonCodec copy = new JsonJacksonCodec(getClass().getClassLoader(), restricted);

            Assertions.assertInstanceOf(TestPerson.class, roundTrip(copy, new TestPerson("John", 30)));
            Assertions.assertThrows(JsonMappingException.class, () -> roundTrip(copy, new TestAddress("NY")));
        }

        @Test
        public void testForbiddenClassOfAnnotatedTypeInfoIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(
                    new HashSet<>(Arrays.asList(TestAnnotatedWrapper.class.getName())));

            TestTracked.instantiated = false;
            String json = "{\"@class\":\"" + TestAnnotatedWrapper.class.getName() + "\",\"payload\":{\"@class\":\""
                    + TestTracked.class.getName() + "\",\"value\":\"boom\"}}";

            Assertions.assertThrows(JsonMappingException.class, () -> decode(restricted, json));
            Assertions.assertFalse(TestTracked.instantiated);
        }

        @Test
        public void testForbiddenClassIsNotLoaded() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            // the class name is built by hand, referencing the class would load it
            String neverLoaded = JsonJacksonCodecTest.class.getName() + "$TestNeverLoaded";
            String json = "{\"@class\":\"" + neverLoaded + "\",\"value\":\"boom\"}";

            Assertions.assertThrows(JsonMappingException.class, () -> decode(restricted, json));
            Assertions.assertFalse(TestLoadFlag.loaded);
        }

        @Test
        public void testDeeplyNestedTypeIdIsRejected() {
            JsonJacksonCodec restricted = new JsonJacksonCodec(allowed);

            StringBuilder typeId = new StringBuilder();
            for (int i = 0; i < 120; i++) {
                typeId.append("java.util.ArrayList<");
            }
            typeId.append("java.lang.String");
            for (int i = 0; i < 120; i++) {
                typeId.append('>');
            }

            Assertions.assertThrows(JsonMappingException.class,
                    () -> decode(restricted, "[\"" + typeId + "\",[]]"));
        }

        @Test
        public void testAllowedClassesAreCopied() throws IOException {
            Set<String> defined = new HashSet<>(Arrays.asList(TestPerson.class.getName()));
            JsonJacksonCodec restricted = new JsonJacksonCodec(defined);

            defined.add(TestAddress.class.getName());

            Assertions.assertThrows(JsonMappingException.class, () -> roundTrip(restricted, new TestAddress("NY")));
            Assertions.assertInstanceOf(TestPerson.class, roundTrip(restricted, new TestPerson("John", 30)));
        }

        @Test
        public void testObjectMapperConstructorKeepsAllowedClasses() throws IOException {
            JsonJacksonCodec restricted = new JsonJacksonCodec(new ObjectMapper(), true, allowed);

            Assertions.assertInstanceOf(TestPerson.class, roundTrip(restricted, new TestPerson("John", 30)));
            Assertions.assertThrows(JsonMappingException.class, () -> roundTrip(restricted, new TestAddress("NY")));
        }
    }
}
