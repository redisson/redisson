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
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import org.apache.fory.exception.ForyException;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonBuilder;
import org.apache.fory.reflect.ObjectInstantiator;
import org.apache.fory.reflect.ObjectInstantiators;
import org.redisson.cache.LRUCacheMap;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.UnaryOperator;

/**
 * <a href="https://fory.apache.org">Apache Fory</a> JSON codec.
 * <p>
 * Stores the class name of every value whose type a JSON document can't express, which allows arbitrary
 * objects to be restored without declaring their type upfront. The format follows {@link JsonJackson3Codec}
 * and {@link JsonJacksonCodec}: a value serialized as a JSON object carries an inlined
 * <code>"@class"</code> property, any other value is wrapped into a <code>["className", value]</code>
 * array, and a value of a type a JSON document expresses natively is stored as-is.
 * <pre>
 *     {"@class":"org.example.Person","name":"John","age":30}
 *     ["java.util.ArrayList",["one","two"]]
 *     "plain value of a natural type"
 * </pre>
 * Apache Fory intentionally supports no open polymorphism, the type information is therefore added by this
 * codec. A collection or a map holding values that need it is written member by member, everything else is
 * serialized by Apache Fory in a single call.
 * <p>
 * Use {@link TypedJsonForyCodec} to store data without the type information.
 * <p>
 * Fully thread-safe.
 *
 * @author Nikita Koksharov
 *
 */
public class JsonForyCodec extends BaseCodec {

    private static final String TYPE_PROPERTY = "@class";

    public static final int DEFAULT_MAX_DEPTH = 1000;

    private static final int MAX_CACHED_CLASSES = 1024;

    public static final JsonForyCodec INSTANCE = new JsonForyCodec();

    private static final byte[] NULL = "null".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TRUE = "true".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FALSE = "false".getBytes(StandardCharsets.UTF_8);
    private static final byte[] TYPE_PROPERTY_PREFIX = ("{\"" + TYPE_PROPERTY + "\":").getBytes(StandardCharsets.UTF_8);
    private static final byte[] TYPE_PROPERTY_NAME = ("\"" + TYPE_PROPERTY + "\"").getBytes(StandardCharsets.UTF_8);

    private static final Set<Class<?>> NATURAL_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            String.class, Integer.class, Double.class, Boolean.class)));

    /**
     * Value types of the JDK this codec stores a class name for on its own.
     */
    private static final Set<String> IMPLICITLY_ALLOWED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "java.lang.Object", "java.lang.String", "java.lang.Number", "java.lang.Boolean",
            "java.lang.Byte", "java.lang.Character", "java.lang.Short", "java.lang.Integer",
            "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.util.UUID", "java.util.Date")));

    private final ForyJson foryJson;
    private final ClassLoader classLoader;

    final Set<String> allowedClasses;

    private static final Field[] WHOLE_OBJECT = new Field[0];

    private static final Map<Class<?>, Class<?>> BOXED_TYPES;

    private static final Map<Class<?>, Object> DEFAULT_VALUES;

    private static final Map<Class<?>, UnaryOperator<Object>> RESTORERS;

    private static final String UNMODIFIABLE_VIEW = Collections.class.getName() + "$Unmodifiable";

    private static final String IMMUTABLE_COLLECTION = "java.util.ImmutableCollections$";

    static {
        Map<Class<?>, Class<?>> boxed = new HashMap<>();
        boxed.put(boolean.class, Boolean.class);
        boxed.put(byte.class, Byte.class);
        boxed.put(char.class, Character.class);
        boxed.put(short.class, Short.class);
        boxed.put(int.class, Integer.class);
        boxed.put(long.class, Long.class);
        boxed.put(float.class, Float.class);
        boxed.put(double.class, Double.class);
        BOXED_TYPES = Collections.unmodifiableMap(boxed);

        Map<Class<?>, Object> defaults = new HashMap<>();
        defaults.put(boolean.class, Boolean.FALSE);
        defaults.put(byte.class, (byte) 0);
        defaults.put(char.class, (char) 0);
        defaults.put(short.class, (short) 0);
        defaults.put(int.class, 0);
        defaults.put(long.class, 0L);
        defaults.put(float.class, 0F);
        defaults.put(double.class, 0D);
        DEFAULT_VALUES = Collections.unmodifiableMap(defaults);

        Map<Class<?>, UnaryOperator<Object>> restorers = new HashMap<>();
        restorers.put(Collections.emptyList().getClass(), members -> Collections.emptyList());
        restorers.put(Collections.emptySet().getClass(), members -> Collections.emptySet());
        restorers.put(Collections.emptyMap().getClass(), members -> Collections.emptyMap());
        // a document holding a different number of members than the stored type takes is left as it was
        // read, which is what the caller does with any other type it cannot rebuild
        restorers.put(Collections.singletonList(null).getClass(), members -> {
            Collection<?> elements = (Collection<?>) members;
            if (elements.size() != 1) {
                return members;
            }
            return Collections.singletonList(elements.iterator().next());
        });
        restorers.put(Collections.singleton(null).getClass(), members -> {
            Collection<?> elements = (Collection<?>) members;
            if (elements.size() != 1) {
                return members;
            }
            return Collections.singleton(elements.iterator().next());
        });
        restorers.put(Collections.singletonMap(null, null).getClass(), members -> {
            Map<?, ?> entries = (Map<?, ?>) members;
            if (entries.size() != 1) {
                return members;
            }
            Map.Entry<?, ?> only = entries.entrySet().iterator().next();
            return Collections.singletonMap(only.getKey(), only.getValue());
        });
        RESTORERS = Collections.unmodifiableMap(restorers);
    }

    private final Map<String, Class<?>> resolvedClasses = new LRUCacheMap<>(MAX_CACHED_CLASSES, 0, 0);
    private final Map<Class<?>, Class<?>> instantiableClasses = new LRUCacheMap<>(MAX_CACHED_CLASSES, 0, 0);
    private final Map<Class<?>, ObjectInstantiator<?>> instantiators = new LRUCacheMap<>(MAX_CACHED_CLASSES, 0, 0);
    private final Map<Class<?>, Field[]> serializedFields = new LRUCacheMap<>(MAX_CACHED_CLASSES, 0, 0);

    private final Encoder encoder = in -> {
        ByteBuf out = ByteBufAllocator.DEFAULT.buffer();
        boolean written = false;
        try {
            writeValue(out, in, 0);
            written = true;
            return out;
        } catch (ForyException | IllegalArgumentException | DateTimeException e) {
            throw new IOException("Unable to write the value", e);
        } finally {
            // releases the buffer on an Error too, which Apache Fory raises on a self-referencing value
            if (!written) {
                out.release();
            }
        }
    };

    private final Decoder<Object> decoder = (buf, state) -> {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        try {
            JsonReader reader = new JsonReader(data);
            Object value = read(reader, 0);
            if (skipWhitespace(data, reader.position, data.length) != data.length) {
                throw new IOException("Unexpected data after the JSON value at position " + reader.position);
            }
            return value;
        } catch (ForyException | IllegalArgumentException | DateTimeException e) {
            // the decoder contract is an IOException, Apache Fory and the JDK report malformed data
            // with an unchecked exception. Anything else is a defect of this codec and stays unchecked.
            throw new IOException("Unable to read the stored value", e);
        }
    };

    public JsonForyCodec() {
        this(null, Collections.emptySet());
    }

    /**
     * Creates a codec restricted to the defined class names.
     *
     * @param allowedClasses class names allowed for serialization and deserialization
     */
    public JsonForyCodec(Set<String> allowedClasses) {
        this(null, allowedClasses);
    }

    /**
     * Creates a codec with the specified class loader.
     *
     * @param classLoader the class loader used to resolve stored class names
     */
    public JsonForyCodec(ClassLoader classLoader) {
        this(classLoader, Collections.emptySet());
    }

    /**
     * Creates a codec with the specified class loader and existing codec for cloning.
     *
     * @param classLoader the class loader to use
     * @param codec the existing codec to copy settings from
     */
    public JsonForyCodec(ClassLoader classLoader, JsonForyCodec codec) {
        this(classLoader, codec.allowedClasses);
    }

    /**
     * Creates a codec with the specified class loader restricted to the defined class names.
     *
     * @param classLoader the class loader used to resolve stored class names
     * @param allowedClasses class names allowed for serialization and deserialization
     */
    public JsonForyCodec(ClassLoader classLoader, Set<String> allowedClasses) {
        this.classLoader = classLoader;
        if (allowedClasses == null) {
            this.allowedClasses = Collections.emptySet();
        } else {
            this.allowedClasses = new HashSet<>(allowedClasses);
        }

        // the type information adds a JSON array level per collection level, so Apache Fory has to
        // accept twice the depth this codec allows
        ForyJsonBuilder builder = ForyJson.builder()
                                          .maxDepth(2 * DEFAULT_MAX_DEPTH)
                                          .withFieldMode(true);
        if (classLoader != null) {
            builder.withClassLoader(classLoader);
        }
        if (!this.allowedClasses.isEmpty()) {
            builder.withTypeChecker((className, context) -> isAllowed(className));
        }
        this.foryJson = createForyJson(builder);
    }

    protected ForyJson createForyJson(ForyJsonBuilder builder) {
        return builder.build();
    }

    public ForyJson getForyJson() {
        return foryJson;
    }

    protected boolean useTypeInfo(Object value) {
        Class<?> type = declaredType(value.getClass());
        if (!NATURAL_TYPES.contains(type)) {
            return true;
        }
        return type == Double.class && !Double.isFinite((Double) value);
    }

    private static boolean ambiguousType(Class<?> type) {
        if (type == UUID.class) {
            return true;
        }
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        return !component.isPrimitive() && !Modifier.isFinal(component.getModifiers());
    }

    private void writeValue(ByteBuf out, Object in, int depth) throws IOException {
        if (depth > DEFAULT_MAX_DEPTH) {
            throw new IOException("JSON max depth " + DEFAULT_MAX_DEPTH + " exceeded");
        }
        if (in == null) {
            out.writeBytes(NULL);
            return;
        }

        Class<?> type = declaredType(in.getClass());

        // nothing tells the decoder what it is reading except the document, so a value carries its class
        // name unless a JSON document expresses its type natively. Jackson instead settles a value stored
        // on its own by its type being non-final, which leaves a final class unreadable: it comes back as
        // a map rather than as itself
        String typeId = null;
        if (useTypeInfo(in) || (depth == 0 && ambiguousType(type))) {
            typeId = type.getName();
        }

        // Apache Fory has no notion of the stored class name, so a collection, an array or a map holding
        // values needing one is written member by member. Anything else is serialized in a single call.
        if (in instanceof Map && writeMemberByMember((Map<?, ?>) in)) {
            writeMap(out, (Map<?, ?>) in, typeId, depth);
            return;
        }

        Collection<?> members = null;
        Class<?> component = null;
        if (in instanceof Collection) {
            members = (Collection<?>) in;
        } else if (type.isArray()) {
            component = type.getComponentType();
            // an array of a primitive type can't hold a value needing a class name
            if (!component.isPrimitive()) {
                members = Arrays.asList((Object[]) in);
            }
        }
        if (members != null && writeMemberByMember(members, component)) {
            writeCollection(out, members, component, typeId, depth);
            return;
        }

        Field[] fields = serializedFields(type);
        if (fields != WHOLE_OBJECT) {
            writeFields(out, in, fields, typeId, depth);
            return;
        }

        if (typeId == null) {
            writeJson(in, type, new ByteBufOutputStream(out));
        } else {
            writeTypedValue(out, in, type, typeId);
        }
    }

    private void writeFields(ByteBuf out, Object in, Field[] fields, String typeId, int depth) throws IOException {
        out.writeByte('{');
        boolean first = true;
        if (typeId != null) {
            out.writeBytes(TYPE_PROPERTY_NAME);
            out.writeByte(':');
            writeString(out, typeId);
            first = false;
        }
        for (Field field : fields) {
            Object value;
            try {
                value = field.get(in);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                throw new IOException("Unable to read field " + field.getName()
                        + " of " + in.getClass().getName(), e);
            }
            // Apache Fory leaves a null field out of the document
            if (value == null) {
                continue;
            }
            if (!first) {
                out.writeByte(',');
            }
            first = false;
            writeString(out, field.getName());
            out.writeByte(':');
            if (ambiguousType(field.getType())) {
                writeValue(out, value, depth + 1);
            } else {
                // the declared type settles what the value is, so it is stored without a class name
                writeJson(value, declaredType(value.getClass()), new ByteBufOutputStream(out));
            }
        }
        out.writeByte('}');
    }

    private Field[] serializedFields(Class<?> type) {
        Field[] cached = serializedFields.get(type);
        if (cached != null) {
            return cached;
        }

        Field[] fields = collectFields(type);
        serializedFields.put(type, fields);
        return fields;
    }

    /**
     * Decides which fields of a class this codec writes one at a time, or {@link #WHOLE_OBJECT} when
     * Apache Fory serializes it in a single call.
     */
    private static Field[] collectFields(Class<?> type) {
        // Apache Fory has a representation of its own for these, rather than storing them as the object
        // their fields describe
        if (type.isEnum() || type.isPrimitive() || type.isArray() || type.isInterface()) {
            return WHOLE_OBJECT;
        }
        if (Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Date.class.isAssignableFrom(type)
                || Calendar.class.isAssignableFrom(type)
                || TemporalAccessor.class.isAssignableFrom(type)
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)) {
            return WHOLE_OBJECT;
        }
        // the rest of the JDK is left alone, reflecting into another module is refused anyway. This also
        // covers Boolean, Character and UUID, which are final and named java.*, so the tests above need
        // no case of their own for them. Narrowing this test means putting those three back
        String typeName = type.getName();
        if (typeName.startsWith("java.") || typeName.startsWith("javax.")
                || typeName.startsWith("jdk.") || typeName.startsWith("sun.")) {
            return WHOLE_OBJECT;
        }

        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.addFirst(c);
        }

        // the declaration order of the superclass first, matching the document Apache Fory would write
        Map<String, Field> byName = new LinkedHashMap<>();
        boolean writtenHere = false;
        for (Class<?> c : hierarchy) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                // a hidden field leaves the most derived one, Apache Fory rejects the duplicate name
                if (byName.put(field.getName(), field) != null) {
                    writtenHere = true;
                }
                // Apache Fory reads an object without assigning its final fields, which drops their
                // value without reporting anything
                if (Modifier.isFinal(modifiers) || ambiguousType(field.getType())) {
                    writtenHere = true;
                }
            }
        }
        if (!writtenHere) {
            return WHOLE_OBJECT;
        }

        Field[] fields = byName.values().toArray(new Field[0]);
        for (Field field : fields) {
            try {
                field.setAccessible(true);
            } catch (Exception e) {
                // a module that isn't open to this one, leave Apache Fory to report what it can't do
                return WHOLE_OBJECT;
            }
        }
        return fields;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeTypedValue(ByteBuf out, Object in, Class<?> type, String typeId) {
        byte[] payload;
        if (type == in.getClass()) {
            payload = foryJson.toJsonBytes(in);
        } else {
            // an enum constant with a class body is an anonymous subclass, serialize it as the enum itself
            payload = foryJson.toJsonBytes(in, (Class) type);
        }
        boolean jsonObject = payload.length > 0 && payload[0] == '{';
        if (!jsonObject) {
            out.writeByte('[');
            writeString(out, typeId);
            out.writeByte(',');
            out.writeBytes(payload);
            out.writeByte(']');
            return;
        }

        out.writeBytes(TYPE_PROPERTY_PREFIX);
        writeString(out, typeId);
        boolean emptyObject = payload.length <= 2;
        if (emptyObject) {
            out.writeByte('}');
        } else {
            out.writeByte(',');
            out.writeBytes(payload, 1, payload.length - 1);
        }
    }

    private void writeMap(ByteBuf out, Map<?, ?> map, String typeId, int depth) throws IOException {
        out.writeByte('{');
        boolean first = true;
        if (typeId != null) {
            out.writeBytes(TYPE_PROPERTY_NAME);
            out.writeByte(':');
            writeString(out, typeId);
            first = false;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = null;
            if (entry.getKey() != null) {
                key = entry.getKey().toString();
            }
            if (key == null) {
                throw new IOException("JSON map key cannot be null");
            }
            if (!first) {
                out.writeByte(',');
            }
            first = false;
            writeString(out, key);
            out.writeByte(':');
            writeValue(out, entry.getValue(), depth + 1);
        }
        out.writeByte('}');
    }

    private void writeCollection(ByteBuf out, Collection<?> members, Class<?> component,
                                 String typeId, int depth) throws IOException {
        if (typeId != null) {
            out.writeByte('[');
            writeString(out, typeId);
            out.writeByte(',');
        }
        // the declared element type is the same for every element, so it is settled once rather than per
        // element. It settles them here only for an array of arrays: every array class carries ABSTRACT,
        // so the caller routes one here for Apache Fory's sake even when the leaf component is final
        boolean elementTypeSettles = component != null && !ambiguousType(component);
        out.writeByte('[');
        boolean first = true;
        for (Object member : members) {
            if (!first) {
                out.writeByte(',');
            }
            first = false;
            if (member != null && (elementTypeSettles || !useTypeInfo(member))) {
                writeJson(member, declaredType(member.getClass()), new ByteBufOutputStream(out));
            } else {
                writeValue(out, member, depth + 1);
            }
        }
        out.writeByte(']');
        if (typeId != null) {
            out.writeByte(']');
        }
    }

    private void writeString(ByteBuf out, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // a surrogate is left to Apache Fory, writeUtf8 would silently replace an unpaired one
            if (c == '"' || c == '\\' || c < 0x20 || Character.isSurrogate(c)) {
                out.writeBytes(foryJson.toJsonBytes(value));
                return;
            }
        }
        out.writeByte('"');
        ByteBufUtil.writeUtf8(out, value);
        out.writeByte('"');
    }

    private boolean writeMemberByMember(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                return true;
            }
            Object value = entry.getValue();
            if (value != null && useTypeInfo(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean writeMemberByMember(Collection<?> members, Class<?> component) {
        // Apache Fory can't serialize an array declaring a component type it can't resolve to a codec
        if (component != null && (component.isInterface() || Modifier.isAbstract(component.getModifiers()))) {
            return true;
        }
        if (component != null && !ambiguousType(component)) {
            // the declared element type settles what every element is, so none of them names its class
            // and Apache Fory can write the whole array in one call
            return false;
        }
        for (Object member : members) {
            if (member != null && useTypeInfo(member)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void writeJson(Object value, Class<?> type, OutputStream out) {
        if (type == value.getClass()) {
            foryJson.writeJsonTo(value, out);
        } else {
            foryJson.writeJsonTo(value, (Class) type, out);
        }
    }

    protected void writeJson(Object value, OutputStream out) {
        if (value == null) {
            foryJson.writeJsonTo(null, out);
            return;
        }
        writeJson(value, declaredType(value.getClass()), out);
    }

    private static final class JsonReader {

        private final byte[] data;
        private final int end;
        private int position;

        JsonReader(byte[] data) {
            this.data = data;
            this.end = data.length;
        }
    }

    private Object read(JsonReader reader, int depth) throws IOException {
        if (depth > DEFAULT_MAX_DEPTH) {
            throw new IOException("JSON max depth " + DEFAULT_MAX_DEPTH + " exceeded");
        }
        reader.position = skipWhitespace(reader.data, reader.position, reader.end);
        if (reader.position == reader.end) {
            return null;
        }
        byte b = reader.data[reader.position];
        if (b == '{') {
            return readObject(reader, depth);
        }
        if (b == '[') {
            return readArray(reader, depth);
        }
        return readScalar(reader);
    }

    private Object readObject(JsonReader reader, int depth) throws IOException {
        byte[] data = reader.data;
        reader.position = skipWhitespace(data, reader.position + 1, reader.end);

        Class<?> type = readTypeProperty(reader);
        if (type == null) {
            Map<Object, Object> map = new LinkedHashMap<>();
            readMembers(reader, depth, map);
            return map;
        }

        Class<?> target = instantiableType(type);
        if (Map.class.isAssignableFrom(target)) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) newInstance(target);
            readMembers(reader, depth, map);
            return restore(type, map);
        }

        Field[] fields = serializedFields(target);
        if (fields != WHOLE_OBJECT) {
            return readFields(reader, depth, target, fields);
        }

        // the remaining properties are handed to Apache Fory as a standalone document, which keeps the
        // exact representation of every value they hold
        int members = reader.position;
        int end = skipToEnd(reader);
        byte[] payload;
        if (members >= end - 1) {
            payload = new byte[]{'{', '}'};
        } else {
            payload = new byte[end - members + 1];
            payload[0] = '{';
            System.arraycopy(data, members, payload, 1, end - members);
        }
        return fromJson(payload, target);
    }

    private Class<?> readTypeProperty(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int quote = endOfString(data, reader.position, reader.end);
        if (quote < 0 || !matches(data, reader.position, quote + 1, TYPE_PROPERTY_NAME)) {
            return null;
        }
        int colon = skipWhitespace(data, quote + 1, reader.end);
        if (colon == reader.end || data[colon] != ':') {
            return null;
        }
        int value = skipWhitespace(data, colon + 1, reader.end);
        int valueEnd = endOfString(data, value, reader.end);
        if (valueEnd < 0) {
            return null;
        }

        Class<?> type = resolveClass(readString(data, value, valueEnd), true);
        reader.position = skipWhitespace(data, valueEnd + 1, reader.end);
        if (reader.position < reader.end && data[reader.position] == ',') {
            reader.position = skipWhitespace(data, reader.position + 1, reader.end);
        }
        return type;
    }

    private Class<?> readWrapperType(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int quote = endOfString(data, reader.position, reader.end);
        if (quote < 0) {
            return null;
        }
        int comma = skipWhitespace(data, quote + 1, reader.end);
        if (comma == reader.end || data[comma] != ',') {
            return null;
        }
        Class<?> type = resolveClass(readString(data, reader.position, quote), false);
        // a value of a natural type is stored without a class name, so a leading string naming one is an
        // element of a plain array rather than a wrapper. A double is the exception the set documents:
        // a non-finite one does carry its class name, so a wrapper naming Double is real
        if (type == null || (NATURAL_TYPES.contains(type) && type != Double.class)) {
            return null;
        }

        reader.position = skipWhitespace(data, comma + 1, reader.end);
        return type;
    }

    private String memberName(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int quote = endOfString(data, reader.position, reader.end);
        if (quote < 0) {
            throw new IOException("Expected a JSON member name at position " + reader.position);
        }
        String name = readString(data, reader.position, quote);

        int colon = skipWhitespace(data, quote + 1, reader.end);
        if (colon == reader.end || data[colon] != ':') {
            throw new IOException("Expected ':' at position " + colon);
        }
        reader.position = skipWhitespace(data, colon + 1, reader.end);
        return name;
    }

    private void readMembers(JsonReader reader, int depth, Map<Object, Object> map) throws IOException {
        byte[] data = reader.data;
        while (reader.position < reader.end && data[reader.position] != '}') {
            String name = memberName(reader);
            Object member = read(reader, depth + 1);
            try {
                map.put(name, member);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                     | IllegalStateException | UnsupportedOperationException e) {
                throw new IOException("Unable to store member " + name + " into " + map.getClass().getName(), e);
            }

            if (!nextMember(reader)) {
                break;
            }
        }
        closeBracket(reader, (byte) '}', "object");
    }

    private Object readFields(JsonReader reader, int depth, Class<?> target, Field[] fields) throws IOException {
        // a record is built from its components, its fields can't be assigned after construction
        boolean record = isRecord(target);
        Object[] components = null;
        Object instance = null;
        if (record) {
            components = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) {
                components[i] = DEFAULT_VALUES.get(fields[i].getType());
            }
        } else {
            instance = newInstance(target);
        }

        byte[] data = reader.data;
        while (reader.position < reader.end && data[reader.position] != '}') {
            String name = memberName(reader);

            int index = -1;
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals(name)) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                // an unknown property is ignored, the way Jackson ignores one
                skipValue(reader);
            } else if (record) {
                components[index] = readFieldValue(reader, depth, fields[index]);
            } else {
                storeField(reader, depth, instance, fields[index]);
            }

            if (!nextMember(reader)) {
                break;
            }
        }
        closeBracket(reader, (byte) '}', "object");

        if (record) {
            return newRecord(target, components);
        }
        return instance;
    }

    private static boolean isRecord(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        return parent != null && "java.lang.Record".equals(parent.getName());
    }

    private Object newRecord(Class<?> target, Object[] components) throws IOException {
        try {
            ObjectInstantiator<?> instantiator = instantiators.get(target);
            if (instantiator == null) {
                instantiator = ObjectInstantiators.getObjectInstantiator(target);
                instantiators.put(target, instantiator);
            }
            return instantiator.newInstanceWithArguments(components);
        } catch (Exception e) {
            throw new IOException("Unable to instantiate " + target.getName(), e);
        }
    }

    private Object readFieldValue(JsonReader reader, int depth, Field field) throws IOException {
        if (ambiguousType(field.getType())) {
            return read(reader, depth + 1);
        }
        // the declared type settles what the value is, so the exact bytes reach Apache Fory, which
        // parses them the way it wrote them
        int from = reader.position;
        int end = skipValue(reader);
        return fromJson(Arrays.copyOfRange(reader.data, from, end),
                BOXED_TYPES.getOrDefault(field.getType(), field.getType()));
    }

    private void storeField(JsonReader reader, int depth, Object instance, Field field) throws IOException {
        Object value = readFieldValue(reader, depth, field);
        if (value == null && field.getType().isPrimitive()) {
            return;
        }
        try {
            field.set(instance, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new IOException("Unable to store member " + field.getName()
                    + " into " + instance.getClass().getName(), e);
        }
    }

    private static boolean nextMember(JsonReader reader) {
        reader.position = skipWhitespace(reader.data, reader.position, reader.end);
        if (reader.position == reader.end || reader.data[reader.position] != ',') {
            return false;
        }
        reader.position = skipWhitespace(reader.data, reader.position + 1, reader.end);
        return true;
    }

    private static void closeBracket(JsonReader reader, byte bracket, String shape) throws IOException {
        if (reader.position == reader.end || reader.data[reader.position] != bracket) {
            throw new IOException("Unterminated JSON " + shape + " at position " + reader.position);
        }
        reader.position++;
    }

    private Object readArray(JsonReader reader, int depth) throws IOException {
        byte[] data = reader.data;
        int start = skipWhitespace(data, reader.position + 1, reader.end);
        reader.position = start;

        Class<?> type = readWrapperType(reader);
        if (type != null) {
            String name = readString(data, start, endOfString(data, start, reader.end));
            Class<?> target = instantiableType(type);
            boolean objectArray = target.isArray() && ambiguousType(target.getComponentType());
            if (Collection.class.isAssignableFrom(target) || objectArray) {
                return readWrappedMembers(reader, depth, type, target, name);
            }
            return readWrappedValue(reader, depth, target, name);
        }

        List<Object> list = new ArrayList<>();
        readElements(reader, depth, list);
        return list;
    }

    private Object readWrappedValue(JsonReader reader, int depth, Class<?> target, String name)
            throws IOException {
        // a wrapped value is a JSON object or array only for an array of a primitive type and for a map;
        // any other shape means the leading string is a plain element rather than a class name
        boolean parsedByFory = true;
        boolean shapeDecides = !target.isArray() && !Map.class.isAssignableFrom(target);
        if (shapeDecides && reader.position < reader.end) {
            byte shape = reader.data[reader.position];
            parsedByFory = shape != '{' && shape != '[';
        }
        if (!parsedByFory) {
            return readPlainArray(reader, depth, name);
        }

        int from = reader.position;
        int end = skipValue(reader);
        if (closes(reader)) {
            return fromJson(Arrays.copyOfRange(reader.data, from, end), target);
        }

        reader.position = from;
        return readPlainArray(reader, depth, name);
    }

    private Object readWrappedMembers(JsonReader reader, int depth, Class<?> stored, Class<?> target,
                                      String name) throws IOException {
        if (reader.position == reader.end || reader.data[reader.position] != '[') {
            return readPlainArray(reader, depth, name);
        }
        reader.position = skipWhitespace(reader.data, reader.position + 1, reader.end);
        List<Object> members = new ArrayList<>();
        readElements(reader, depth, members);

        if (!closes(reader)) {
            // the array holds more than a class name and a value, so the leading string is a plain element
            List<Object> list = new ArrayList<>();
            list.add(name);
            list.add(members);
            readMoreElements(reader, depth, list);
            return list;
        }

        if (target.isArray()) {
            return toArray(members, target.getComponentType());
        }
        @SuppressWarnings("unchecked")
        Collection<Object> collection = (Collection<Object>) newInstance(target);
        try {
            collection.addAll(members);
        } catch (NullPointerException | ClassCastException | IllegalArgumentException
                 | IllegalStateException | UnsupportedOperationException e) {
            throw new IOException("Unable to store the elements into " + target.getName(), e);
        }
        return restore(stored, collection);
    }

    private Object readPlainArray(JsonReader reader, int depth, String name) throws IOException {
        List<Object> list = new ArrayList<>();
        list.add(name);
        readMoreElements(reader, depth, list);
        return list;
    }

    private void readMoreElements(JsonReader reader, int depth, List<Object> list) throws IOException {
        nextMember(reader);
        readElements(reader, depth, list);
    }

    private static boolean closes(JsonReader reader) {
        reader.position = skipWhitespace(reader.data, reader.position, reader.end);
        if (reader.position < reader.end && reader.data[reader.position] == ']') {
            reader.position++;
            return true;
        }
        return false;
    }

    private void readElements(JsonReader reader, int depth, Collection<Object> target) throws IOException {
        byte[] data = reader.data;
        while (reader.position < reader.end && data[reader.position] != ']') {
            Object member = read(reader, depth + 1);
            try {
                target.add(member);
            } catch (NullPointerException | ClassCastException | IllegalArgumentException
                     | IllegalStateException | UnsupportedOperationException e) {
                throw new IOException("Unable to store an element into " + target.getClass().getName(), e);
            }

            if (!nextMember(reader)) {
                break;
            }
        }
        closeBracket(reader, (byte) ']', "array");
    }

    @SuppressWarnings("unchecked")
    private Object toArray(List<Object> members, Class<?> component) throws IOException {
        try {
            Object array = Array.newInstance(component, members.size());
            for (int i = 0; i < members.size(); i++) {
                Object member = members.get(i);
                // only an array whose members can carry a class name is read this way, so a member is
                // already of the component type unless it is a nested array, which arrives as a list
                if (member instanceof List && component.isArray()) {
                    member = toArray((List<Object>) member, component.getComponentType());
                }
                Array.set(array, i, member);
            }
            return array;
        } catch (IllegalArgumentException e) {
            throw new IOException("Unable to store the elements into an array of " + component.getName(), e);
        }
    }

    private Object readScalar(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int from = reader.position;
        if (data[from] == '"') {
            int quote = endOfString(data, from, reader.end);
            if (quote < 0) {
                throw new IOException("Unterminated JSON string at position " + from);
            }
            reader.position = quote + 1;
            return readString(data, from, quote);
        }

        int end = endOfToken(data, from, reader.end);
        reader.position = end;
        if (matches(data, from, end, NULL)) {
            return null;
        }
        if (matches(data, from, end, TRUE)) {
            return Boolean.TRUE;
        }
        if (matches(data, from, end, FALSE)) {
            return Boolean.FALSE;
        }

        return readNumber(data, from, end);
    }

    private static Object readNumber(byte[] data, int from, int end) throws IOException {
        boolean fractional = false;
        for (int i = from; i < end; i++) {
            if (data[i] == '.' || data[i] == 'e' || data[i] == 'E') {
                fractional = true;
                break;
            }
        }

        String text = new String(data, from, end - from, StandardCharsets.UTF_8);
        try {
            if (fractional) {
                return Double.parseDouble(text);
            }
            long number = Long.parseLong(text);
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return (int) number;
            }
            return number;
        } catch (NumberFormatException e) {
            try {
                return new BigInteger(text);
            } catch (NumberFormatException ex) {
                throw new IOException("Unable to read the JSON value at position " + from, ex);
            }
        }
    }

    private static int skipValue(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int from = skipWhitespace(data, reader.position, reader.end);
        if (from == reader.end) {
            throw new IOException("Expected a JSON value at position " + reader.position);
        }
        if (data[from] == '"') {
            int quote = endOfString(data, from, reader.end);
            if (quote < 0) {
                throw new IOException("Unterminated JSON string at position " + from);
            }
            reader.position = quote + 1;
            return reader.position;
        }
        if (data[from] == '{' || data[from] == '[') {
            reader.position = from + 1;
            return skipToEnd(reader);
        }
        reader.position = endOfToken(data, from, reader.end);
        return reader.position;
    }

    private static int skipToEnd(JsonReader reader) throws IOException {
        byte[] data = reader.data;
        int nesting = 1;
        int i = reader.position;
        while (i < reader.end) {
            byte b = data[i];
            if (b == '"') {
                i = endOfString(data, i, reader.end);
                if (i < 0) {
                    throw new IOException("Unterminated JSON string at position " + reader.position);
                }
            } else if (b == '{' || b == '[') {
                nesting++;
            } else if (b == '}' || b == ']') {
                nesting--;
                if (nesting == 0) {
                    reader.position = i + 1;
                    return reader.position;
                }
            }
            i++;
        }
        throw new IOException("Unterminated JSON value at position " + reader.position);
    }

    private static int endOfToken(byte[] data, int from, int to) {
        int i = from;
        while (i < to && data[i] != ',' && data[i] != '}' && data[i] != ']' && !isWhitespace(data[i])) {
            i++;
        }
        return i;
    }

    private static int endOfString(byte[] data, int openingQuote, int to) {
        if (openingQuote >= to || data[openingQuote] != '"') {
            return -1;
        }
        for (int i = openingQuote + 1; i < to; i++) {
            if (data[i] == '\\') {
                i++;
            } else if (data[i] == '"') {
                return i;
            }
        }
        return -1;
    }

    private String readString(byte[] data, int openingQuote, int closingQuote) throws IOException {
        for (int i = openingQuote + 1; i < closingQuote; i++) {
            if (data[i] == '\\') {
                return (String) fromJson(Arrays.copyOfRange(data, openingQuote, closingQuote + 1), String.class);
            }
        }
        return new String(data, openingQuote + 1, closingQuote - openingQuote - 1, StandardCharsets.UTF_8);
    }

    private Object fromJson(byte[] payload, Class<?> type) throws IOException {
        try {
            return foryJson.fromJson(payload, type);
        } catch (Exception e) {
            throw new IOException("Unable to read the stored value", e);
        }
    }

    private static boolean matches(byte[] data, int from, int to, byte[] token) {
        if (to - from != token.length) {
            return false;
        }
        for (int i = 0; i < token.length; i++) {
            if (data[from + i] != token[i]) {
                return false;
            }
        }
        return true;
    }

    private static int skipWhitespace(byte[] data, int position, int to) {
        int i = position;
        while (i < to && isWhitespace(data[i])) {
            i++;
        }
        return i;
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r';
    }

    private Class<?> resolveClass(String className, boolean required) throws IOException {
        Class<?> type = resolve(className);
        if (type == null) {
            if (required) {
                throw new IOException("Unable to resolve class " + className);
            }
            return null;
        }
        if (!isAllowed(className, type)) {
            throw new IOException("Class " + className + " isn't allowed");
        }
        return type;
    }

    private Class<?> resolve(String className) {
        Class<?> type = resolvedClasses.get(className);
        if (type != null) {
            return type;
        }
        try {
            type = Class.forName(className, false, getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        resolvedClasses.put(className, type);
        return type;
    }

    private boolean isAllowed(String className) {
        if (allowedClasses.isEmpty() || allowedClasses.contains(className)) {
            return true;
        }
        Class<?> type = resolve(className);
        if (type == null) {
            return false;
        }
        return isAllowed(className, type);
    }

    private boolean isAllowed(String className, Class<?> type) {
        if (allowedClasses.isEmpty() || allowedClasses.contains(className)) {
            return true;
        }
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        if (component.isPrimitive() || allowedClasses.contains(component.getName())) {
            return true;
        }
        if (component.getClassLoader() != null) {
            return false;
        }
        String name = component.getName();
        if (IMPLICITLY_ALLOWED.contains(name)) {
            return true;
        }
        if (name.startsWith("java.math.") || name.startsWith("java.time.")) {
            return true;
        }
        return name.startsWith("java.util.")
                && (Collection.class.isAssignableFrom(component) || Map.class.isAssignableFrom(component));
    }

    private Class<?> instantiableType(Class<?> type) {
        Class<?> result = instantiableClasses.get(type);
        if (result != null) {
            return result;
        }
        if (type.isArray()) {
            return type;
        }

        boolean instantiable = !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())
                && Modifier.isPublic(type.getModifiers());
        if (instantiable) {
            try {
                instantiable = Modifier.isPublic(type.getDeclaredConstructor().getModifiers());
            } catch (NoSuchMethodException e) {
                instantiable = false;
            }
        }

        result = type;
        if (!instantiable) {
            if (Map.class.isAssignableFrom(type)) {
                result = LinkedHashMap.class;
            } else if (Set.class.isAssignableFrom(type)) {
                result = LinkedHashSet.class;
            } else if (Collection.class.isAssignableFrom(type)) {
                result = ArrayList.class;
            }
        }
        instantiableClasses.put(type, result);
        return result;
    }

    private static Object restore(Class<?> stored, Object members) {
        if (stored == members.getClass()) {
            return members;
        }

        UnaryOperator<Object> restorer = RESTORERS.get(stored);
        if (restorer != null) {
            return restorer.apply(members);
        }
        if (EnumSet.class.isAssignableFrom(stored)) {
            return enumSetOf((Collection<?>) members);
        }

        String name = stored.getName();
        if (!name.startsWith(UNMODIFIABLE_VIEW) && !name.startsWith(IMMUTABLE_COLLECTION)) {
            return members;
        }
        if (members instanceof Map) {
            Map<?, ?> entries = (Map<?, ?>) members;
            if (SortedMap.class.isAssignableFrom(stored)) {
                return Collections.unmodifiableSortedMap(new TreeMap<>(entries));
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
        Collection<?> elements = (Collection<?>) members;
        if (SortedSet.class.isAssignableFrom(stored)) {
            return Collections.unmodifiableSortedSet(new TreeSet<>(elements));
        }
        if (Set.class.isAssignableFrom(stored) || elements instanceof Set) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(elements));
        }
        return Collections.unmodifiableList(new ArrayList<>(elements));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumSetOf(Collection<?> members) {
        // the enum class comes from the members, an empty set leaves no way to name it
        if (members.isEmpty()) {
            return members;
        }
        for (Object member : members) {
            if (!(member instanceof Enum)) {
                return members;
            }
        }
        return EnumSet.copyOf((Collection) members);
    }

    private Object newInstance(Class<?> type) throws IOException {
        try {
            ObjectInstantiator<?> instantiator = instantiators.get(type);
            if (instantiator == null) {
                instantiator = ObjectInstantiators.getObjectInstantiator(type);
                instantiators.put(type, instantiator);
            }
            return instantiator.newInstance();
        } catch (Exception e) {
            throw new IOException("Unable to instantiate " + type.getName(), e);
        }
    }

    private static Class<?> declaredType(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        if (!type.isEnum() && parent != null && parent.isEnum()) {
            return parent;
        }
        return type;
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public ClassLoader getClassLoader() {
        if (classLoader != null) {
            return classLoader;
        }
        return super.getClassLoader();
    }
}
