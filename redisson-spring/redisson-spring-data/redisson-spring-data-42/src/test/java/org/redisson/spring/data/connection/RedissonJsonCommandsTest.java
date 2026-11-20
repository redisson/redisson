package org.redisson.spring.data.connection;

import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisJsonCommands;
import org.springframework.data.redis.connection.json.JsonPath;
import org.springframework.data.redis.connection.json.JsonSetCondition;
import org.springframework.data.redis.connection.json.JsonType;
import org.springframework.data.redis.connection.json.JsonValue;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonJsonCommandsTest extends BaseConnectionTest {

    private static final byte[] KEY = "json".getBytes(StandardCharsets.UTF_8);

    private static final byte[] MISSING = "missing".getBytes(StandardCharsets.UTF_8);

    private static final String DOCUMENT = "{\"a\":[1,2],\"b\":\"str\",\"c\":true,\"d\":null,\"e\":3.5,\"f\":{\"x\":1},\"n\":7}";

    @Test
    public void testSetAndGet() {
        RedisJsonCommands json = connection.jsonCommands();
        assertThat(json.jsonSet(KEY, JsonValue.raw(DOCUMENT))).isTrue();

        assertThat(new String(json.jsonGet(KEY), StandardCharsets.UTF_8)).isEqualTo("[" + DOCUMENT + "]");
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.a")), StandardCharsets.UTF_8)).isEqualTo("[[1,2]]");
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.zz")), StandardCharsets.UTF_8)).isEqualTo("[]");
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.a"), JsonPath.raw("$.b")), StandardCharsets.UTF_8))
                .startsWith("{").endsWith("}").contains("\"$.a\":[[1,2]]").contains("\"$.b\":[\"str\"]");
        assertThat(json.jsonGet(MISSING)).isNull();

        assertThat(json.jsonSet(KEY, JsonPath.raw("$.b"), JsonValue.of("quote\" and \\backslash"), JsonSetCondition.upsert())).isTrue();
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.b")), StandardCharsets.UTF_8))
                .isEqualTo("[\"quote\\\" and \\\\backslash\"]");
    }

    @Test
    public void testSetConditions() {
        RedisJsonCommands json = connection.jsonCommands();
        assertThat(json.jsonSet(KEY, JsonPath.root(), JsonValue.raw("{\"a\":1}"), JsonSetCondition.ifPathExists())).isFalse();
        assertThat(json.jsonSet(KEY, JsonPath.root(), JsonValue.raw("{\"a\":1}"), JsonSetCondition.ifPathNotExists())).isTrue();
        assertThat(json.jsonSet(KEY, JsonPath.root(), JsonValue.raw("{\"a\":0}"), JsonSetCondition.ifPathNotExists())).isFalse();

        assertThat(json.jsonSet(KEY, JsonPath.raw("$.a"), JsonValue.of(2), JsonSetCondition.ifPathNotExists())).isFalse();
        assertThat(json.jsonSet(KEY, JsonPath.raw("$.a"), JsonValue.of(2), JsonSetCondition.ifPathExists())).isTrue();
        assertThat(json.jsonSet(KEY, JsonPath.raw("$.b"), JsonValue.of(3), JsonSetCondition.ifPathExists())).isFalse();
        assertThat(json.jsonSet(KEY, JsonPath.raw("$.b"), JsonValue.of(3), JsonSetCondition.ifPathNotExists())).isTrue();
        assertThat(json.jsonSet(KEY, JsonPath.raw("$.c"), JsonValue.nullValue(), JsonSetCondition.upsert())).isTrue();

        assertThat(new String(json.jsonGet(KEY), StandardCharsets.UTF_8)).isEqualTo("[{\"a\":2,\"b\":3,\"c\":null}]");

        assertThatThrownBy(() -> json.jsonSet(MISSING, JsonPath.raw("$.a"), JsonValue.of(1), JsonSetCondition.upsert()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    public void testArrays() {
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw(DOCUMENT));

        assertThat(json.jsonArrAppend(KEY, JsonPath.raw("$.a"), JsonValue.of(3), JsonValue.of(4))).containsExactly(4L);
        assertThat(json.jsonArrAppend(KEY, JsonPath.raw("$.b"), JsonValue.of(3))).containsExactly((Long) null);
        assertThat(json.jsonArrAppend(KEY, JsonPath.raw("$.zz"), JsonValue.of(3))).isEmpty();

        assertThat(json.jsonArrIndex(KEY, JsonPath.raw("$.a"), JsonValue.of(3))).containsExactly(2L);
        assertThat(json.jsonArrIndex(KEY, JsonPath.raw("$.a"), JsonValue.of(9))).containsExactly(-1L);
        assertThat(json.jsonArrIndex(KEY, JsonPath.raw("$.*"), JsonValue.of(3))).containsExactly(2L, null, null, null, null, null, null);
        assertThat(json.jsonArrIndex(KEY, JsonPath.raw("$.zz"), JsonValue.of(3))).isEmpty();

        assertThat(json.jsonArrInsert(KEY, JsonPath.raw("$.a"), 1, JsonValue.of(7), JsonValue.of(8))).containsExactly(6L);
        assertThat(json.jsonArrInsert(KEY, JsonPath.raw("$.b"), 0, JsonValue.of(7))).containsExactly((Long) null);

        assertThat(json.jsonArrLen(KEY, JsonPath.raw("$.a"))).containsExactly(6L);
        assertThat(json.jsonArrLen(KEY, JsonPath.raw("$.*"))).containsExactly(6L, null, null, null, null, null, null);
        assertThat(json.jsonArrLen(KEY, JsonPath.raw("$.zz"))).isEmpty();

        assertThat(json.jsonArrTrim(KEY, JsonPath.raw("$.a"), 1, 2)).containsExactly(2L);
        assertThat(json.jsonArrTrim(KEY, JsonPath.raw("$.f"), 0, 1)).containsExactly((Long) null);
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.a")), StandardCharsets.UTF_8)).isEqualTo("[[7,8]]");

        assertThatThrownBy(() -> json.jsonArrAppend(MISSING, JsonPath.root(), JsonValue.of(1)))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
        assertThatThrownBy(() -> json.jsonArrLen(MISSING, JsonPath.root()))
                .isInstanceOf(InvalidDataAccessApiUsageException.class);
    }

    @Test
    public void testStrings() {
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw(DOCUMENT));

        assertThat(json.jsonStrLen(KEY, JsonPath.raw("$.b"))).containsExactly(3L);
        assertThat(json.jsonStrLen(KEY, JsonPath.raw("$.*"))).containsExactly(null, 3L, null, null, null, null, null);
        assertThat(json.jsonStrLen(KEY, JsonPath.raw("$.zz"))).isEmpty();

        assertThat(json.jsonStrAppend(KEY, JsonPath.raw("$.b"), "xy")).containsExactly(5L);
        assertThat(json.jsonStrAppend(KEY, JsonPath.raw("$.b"), "\"x\\y")).containsExactly(9L);
        assertThat(json.jsonStrAppend(KEY, JsonPath.raw("$.a"), "xy")).containsExactly((Long) null);
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.b")), StandardCharsets.UTF_8)).isEqualTo("[\"strxy\\\"x\\\\y\"]");
    }

    @Test
    public void testToggle() {
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw(DOCUMENT));

        assertThat(json.jsonToggle(KEY, JsonPath.raw("$.c"))).containsExactly(false);
        assertThat(json.jsonToggle(KEY, JsonPath.raw("$.c"))).containsExactly(true);
        assertThat(json.jsonToggle(KEY, JsonPath.raw("$.*"))).containsExactly(null, null, false, null, null, null, null);
        assertThat(json.jsonToggle(KEY, JsonPath.raw("$.zz"))).isEmpty();
    }

    @Test
    public void testType() {
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw(DOCUMENT));

        assertThat(json.jsonType(KEY)).containsExactly(JsonType.OBJECT);
        assertThat(json.jsonType(KEY, JsonPath.raw("$.*"))).containsExactly(JsonType.ARRAY, JsonType.STRING,
                JsonType.BOOLEAN, JsonType.NULL, JsonType.NUMBER, JsonType.OBJECT, JsonType.NUMBER);
        assertThat(json.jsonType(KEY, JsonPath.raw("$.zz"))).isEmpty();
        assertThat(json.jsonType(MISSING)).isNull();

        assertThat(connection.keyCommands().type(KEY)).isEqualTo(DataType.JSON);
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(ScanOptions.scanOptions().type(DataType.JSON).build())) {
            List<String> keys = new ArrayList<>();
            cursor.forEachRemaining(key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            assertThat(keys).containsExactly("json");
        }
    }

    @Test
    public void testClearDeleteAndMerge() {
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw(DOCUMENT));

        assertThat(json.jsonMerge(KEY, JsonValue.raw("{\"m\":1,\"b\":null}"))).isTrue();
        assertThat(json.jsonMerge(KEY, JsonPath.raw("$.f"), JsonValue.raw("{\"y\":2}"))).isTrue();
        assertThat(json.jsonMerge(KEY, JsonPath.raw("$.zz.q"), JsonValue.raw("{\"y\":2}"))).isFalse();
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.m"), JsonPath.raw("$.b"), JsonPath.raw("$.f")), StandardCharsets.UTF_8))
                .contains("\"$.m\":[1]").contains("\"$.b\":[]").contains("\"$.f\":[{\"x\":1,\"y\":2}]");

        assertThat(json.jsonClear(KEY, JsonPath.raw("$.a"))).isEqualTo(1L);
        assertThat(json.jsonClear(KEY, JsonPath.raw("$.zz"))).isZero();
        assertThat(json.jsonDel(KEY, JsonPath.raw("$.f"))).isEqualTo(1L);
        assertThat(json.jsonDel(KEY, JsonPath.raw("$.zz"))).isZero();
        assertThat(new String(json.jsonGet(KEY, JsonPath.raw("$.a")), StandardCharsets.UTF_8)).isEqualTo("[[]]");

        assertThat(json.jsonClear(KEY)).isEqualTo(1L);
        assertThat(new String(json.jsonGet(KEY), StandardCharsets.UTF_8)).isEqualTo("[{}]");
        assertThat(json.jsonDel(KEY)).isEqualTo(1L);
        assertThat(json.jsonDel(KEY)).isZero();
        assertThat(connection.keyCommands().exists(KEY)).isFalse();

        assertThat(json.jsonMerge(MISSING, JsonValue.raw("{\"m\":1}"))).isTrue();
        assertThat(new String(json.jsonGet(MISSING), StandardCharsets.UTF_8)).isEqualTo("[{\"m\":1}]");
    }

    @Test
    public void testMGet() {
        RedisJsonCommands json = connection.jsonCommands();
        byte[] other = "json2".getBytes(StandardCharsets.UTF_8);
        byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);
        json.jsonSet(KEY, JsonValue.raw("{\"a\":1}"));
        json.jsonSet(other, JsonValue.raw("{\"a\":2}"));
        connection.stringCommands().set(plain, "value".getBytes(StandardCharsets.UTF_8));

        assertThat(json.jsonMGet(KEY, MISSING, other, plain)).containsExactly("[{\"a\":1}]".getBytes(), null,
                "[{\"a\":2}]".getBytes(), null);
        assertThat(json.jsonMGet(JsonPath.raw("$.a"), other, KEY)).containsExactly("[2]".getBytes(), "[1]".getBytes());
        assertThat(json.jsonMGet(JsonPath.raw("$.zz"), KEY)).containsExactly("[]".getBytes());
    }

    @Test
    public void testDeprecatedConnectionMethods() {
        RedisConnection connection = new RedissonConnection(redisson);
        assertThat(connection.jsonSet(KEY, JsonValue.raw("{\"a\":[1]}"))).isTrue();
        assertThat(connection.jsonArrAppend(KEY, JsonPath.raw("$.a"), JsonValue.of(2))).containsExactly(2L);
        assertThat(new String(connection.jsonGet(KEY), StandardCharsets.UTF_8)).isEqualTo("[{\"a\":[1,2]}]");
        assertThat(connection.jsonType(KEY, JsonPath.raw("$.a"))).containsExactly(JsonType.ARRAY);
    }

    @Test
    public void testRejectsInvalidArguments() {
        RedisJsonCommands json = connection.jsonCommands();
        assertThatThrownBy(() -> json.jsonSet(null, JsonValue.raw("{}"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonSet(KEY, JsonPath.root(), JsonValue.raw("{}"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonGet(KEY, new JsonPath[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonArrAppend(KEY, JsonPath.root())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonArrInsert(KEY, JsonPath.root(), 0, JsonValue.of(1), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonMGet(JsonPath.root())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonMGet(JsonPath.root(), KEY, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> json.jsonStrAppend(KEY, null, "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testPipeline() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.openPipeline();
        RedisJsonCommands json = connection.jsonCommands();
        assertThat(json.jsonSet(KEY, JsonValue.raw(DOCUMENT))).isNull();
        queueEveryCommand(json);
        assertResults(connection.closePipeline());
    }

    @Test
    public void testTransaction() {
        RedissonConnection connection = new RedissonConnection(redisson);
        connection.multi();
        RedisJsonCommands json = connection.jsonCommands();
        assertThat(json.jsonSet(KEY, JsonValue.raw(DOCUMENT))).isNull();
        queueEveryCommand(json);
        assertResults(connection.exec());
    }

    @Test
    public void testPipelineWithFilterOkResponses() {
        RedissonConnection connection = new RedissonConnection(redisson, true);
        connection.openPipeline();
        RedisJsonCommands json = connection.jsonCommands();
        json.jsonSet(KEY, JsonValue.raw("{\"a\":1}"));
        json.jsonSet(KEY, JsonPath.raw("$.a"), JsonValue.of(2), JsonSetCondition.ifPathNotExists());
        json.jsonMerge(KEY, JsonValue.raw("{\"b\":2}"));
        json.jsonGet(KEY);
        assertThat(connection.closePipeline()).containsExactly(true, false, true, "[{\"a\":1,\"b\":2}]".getBytes());
    }

    @Test
    public void testResp3() {
        Config config = createConfig();
        config.setProtocol(Protocol.RESP3);
        RedissonClient client = Redisson.create(config);
        try {
            RedissonConnection connection = new RedissonConnection(client);
            RedisJsonCommands json = connection.jsonCommands();
            assertThat(json.jsonSet(KEY, JsonValue.raw(DOCUMENT))).isTrue();

            assertThat(json.jsonType(KEY)).containsExactly(JsonType.OBJECT);
            assertThat(json.jsonType(KEY, JsonPath.raw("$.*"))).containsExactly(JsonType.ARRAY, JsonType.STRING,
                    JsonType.BOOLEAN, JsonType.NULL, JsonType.NUMBER, JsonType.OBJECT, JsonType.NUMBER);
            assertThat(json.jsonType(KEY, JsonPath.raw("$.zz"))).isEmpty();
            assertThat(json.jsonType(MISSING)).isNull();

            assertThat(json.jsonArrAppend(KEY, JsonPath.raw("$.*"), JsonValue.of(3))).containsExactly(3L, null, null, null, null, null, null);
            assertThat(json.jsonToggle(KEY, JsonPath.raw("$.*"))).containsExactly(null, null, false, null, null, null, null);
            assertThat(json.jsonStrLen(KEY, JsonPath.raw("$.b"))).containsExactly(3L);
            assertThat(json.jsonSet(KEY, JsonPath.raw("$.a"), JsonValue.of(1), JsonSetCondition.ifPathNotExists())).isFalse();
            assertThat(json.jsonMerge(KEY, JsonPath.raw("$.zz.q"), JsonValue.raw("{}"))).isFalse();
            assertThat(json.jsonGet(MISSING)).isNull();
            assertThat(json.jsonMGet(JsonPath.raw("$.n"), KEY, MISSING)).containsExactly("[7]".getBytes(), null);

            connection.openPipeline();
            json.jsonType(KEY, JsonPath.raw("$.a"));
            json.jsonType(MISSING);
            json.jsonGet(MISSING);
            assertThat(connection.closePipeline()).containsExactly(Arrays.asList(JsonType.ARRAY), null, null);
        } finally {
            client.shutdown();
        }
    }

    private void queueEveryCommand(RedisJsonCommands json) {
        assertThat(json.jsonArrAppend(KEY, JsonPath.raw("$.a"), JsonValue.of(3))).isNull();
        json.jsonArrIndex(KEY, JsonPath.raw("$.a"), JsonValue.of(3));
        json.jsonArrInsert(KEY, JsonPath.raw("$.a"), 0, JsonValue.of(0));
        json.jsonArrLen(KEY, JsonPath.raw("$.*"));
        json.jsonArrTrim(KEY, JsonPath.raw("$.a"), 0, 1);
        json.jsonStrAppend(KEY, JsonPath.raw("$.b"), "xy");
        json.jsonStrLen(KEY, JsonPath.raw("$.b"));
        json.jsonToggle(KEY, JsonPath.raw("$.c"));
        json.jsonType(KEY, JsonPath.raw("$.n"));
        json.jsonType(MISSING);
        json.jsonMerge(KEY, JsonValue.raw("{\"m\":1}"));
        json.jsonSet(KEY, JsonPath.raw("$.m"), JsonValue.of(2), JsonSetCondition.ifPathNotExists());
        json.jsonGet(KEY, JsonPath.raw("$.a"));
        json.jsonGet(MISSING);
        json.jsonClear(KEY, JsonPath.raw("$.f"));
        json.jsonDel(KEY, JsonPath.raw("$.f"));
        json.jsonMGet(JsonPath.raw("$.m"), KEY, MISSING);
    }

    private void assertResults(List<Object> results) {
        assertThat(results.subList(0, results.size() - 1)).containsExactly(true,
                Arrays.asList(3L),
                Arrays.asList(2L),
                Arrays.asList(4L),
                Arrays.asList(4L, null, null, null, null, null, null),
                Arrays.asList(2L),
                Arrays.asList(5L),
                Arrays.asList(5L),
                Arrays.asList(false),
                Arrays.asList(JsonType.NUMBER),
                null,
                true,
                false,
                "[[0,1]]".getBytes(),
                null,
                1L,
                1L);
        assertThat((List<byte[]>) results.get(results.size() - 1)).containsExactly("[1]".getBytes(), null);
    }

}
