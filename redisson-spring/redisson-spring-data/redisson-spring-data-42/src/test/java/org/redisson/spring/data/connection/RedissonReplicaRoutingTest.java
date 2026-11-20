package org.redisson.spring.data.connection;

import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.RedisDockerTest;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.BitFieldSubCommands.BitFieldType;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisJsonCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.connection.SetCondition;
import org.springframework.data.redis.connection.json.JsonPath;
import org.springframework.data.redis.connection.json.JsonType;
import org.springframework.data.redis.connection.json.JsonValue;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.types.Expiration;
import org.testcontainers.containers.ContainerState;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonReplicaRoutingTest extends RedisDockerTest {

    private static final Pattern CALLS = Pattern.compile("calls=(\\d+)");

    @Test
    public void testSetGetRunsOnMaster() throws InterruptedException {
        withSentinel((nodes, config) -> {
            RedissonClient client = Redisson.create(config);
            try {
                RedisConnection connection = new RedissonConnectionFactory(client).getConnection();
                byte[] key = "setget-routing".getBytes();
                connection.stringCommands().set(key, "v1".getBytes());

                connection.stringCommands().get(key);
                assertThat(calls(nodes.get(0), "get")).isZero();
                assertThat(calls(nodes.get(1), "get")).isPositive();

                byte[] previous = connection.stringCommands().setGet(key, "v2".getBytes(),
                        SetCondition.upsert(), Expiration.persistent());
                assertThat(previous).isEqualTo("v1".getBytes());

                byte[] previousViaOption = connection.stringCommands().setGet(key, "v3".getBytes(),
                        Expiration.persistent(), SetOption.upsert());
                assertThat(previousViaOption).isEqualTo("v2".getBytes());
            } finally {
                client.shutdown();
            }
        }, 1);
    }

    @Test
    public void testWriteCommandsRunOnMaster() throws InterruptedException {
        withSentinel((nodes, config) -> {
            RedissonClient client = Redisson.create(config);
            try {
                RedissonConnectionFactory factory = new RedissonConnectionFactory(client);
                RedisConnection connection = factory.getConnection();
                ReactiveRedisConnection reactive = factory.getReactiveConnection();

                connection.stringCommands().get("routing-precondition".getBytes());
                assertThat(calls(nodes.get(0), "get")).isZero();
                assertThat(calls(nodes.get(1), "get")).isPositive();

                byte[] bits = "bitfield-routing".getBytes();
                BitFieldSubCommands get = BitFieldSubCommands.create().get(BitFieldType.unsigned(8)).valueAt(0);
                BitFieldSubCommands set = BitFieldSubCommands.create().set(BitFieldType.unsigned(8)).valueAt(0).to(7);
                assertThat(connection.stringCommands().bitField(bits, get)).containsExactly(0L);
                assertThat(reactive.stringCommands().bitField(ByteBuffer.wrap(bits), set).block()).containsExactly(0L);
                assertThat(reactive.stringCommands().bitField(ByteBuffer.wrap(bits), get).block()).containsExactly(7L);

                byte[] hash = "hash-routing".getBytes();
                connection.hashCommands().hSet(hash, "field".getBytes(), "value".getBytes());
                long inAnHour = Instant.now().plus(Duration.ofHours(1)).getEpochSecond();
                assertThat(connection.hashCommands().hExpireAt(hash, inAnHour, "field".getBytes())).containsExactly(1L);
                assertThat(reactive.hashCommands().hGetEx(ByteBuffer.wrap(hash), Expiration.seconds(100),
                        Collections.singletonList(ByteBuffer.wrap("field".getBytes()))).block())
                        .containsExactly(ByteBuffer.wrap("value".getBytes()));

                byte[] stream = "xreadgroup-routing".getBytes();
                connection.streamCommands().xGroupCreate(stream, "group", ReadOffset.from("0-0"), true);
                connection.streamCommands().xAdd(stream, Collections.singletonMap("field".getBytes(), "value".getBytes()));
                assertThat(reactive.streamCommands().xReadGroup(Consumer.from("group", "consumer"),
                        StreamOffset.create(ByteBuffer.wrap(stream), ReadOffset.lastConsumed())).collectList().block())
                        .hasSize(1);
            } finally {
                client.shutdown();
            }
        }, 1);
    }

    @Test
    public void testJsonReadsRunOnReplicaAndWritesOnMaster() throws InterruptedException {
        withSentinel((nodes, config) -> {
            RedissonClient client = Redisson.create(config);
            try {
                RedisJsonCommands json = new RedissonConnectionFactory(client).getConnection().jsonCommands();
                byte[] key = "json-routing".getBytes();
                assertThat(json.jsonSet(key, JsonValue.raw("{\"a\":[1],\"s\":\"x\",\"b\":true,\"m\":{}}"))).isTrue();
                assertThat(json.jsonArrAppend(key, JsonPath.raw("$.a"), JsonValue.of(2))).containsExactly(2L);
                assertThat(json.jsonArrInsert(key, JsonPath.raw("$.a"), 0, JsonValue.of(0))).containsExactly(3L);
                assertThat(json.jsonArrTrim(key, JsonPath.raw("$.a"), 0, 1)).containsExactly(2L);
                assertThat(json.jsonStrAppend(key, JsonPath.raw("$.s"), "y")).containsExactly(2L);
                assertThat(json.jsonToggle(key, JsonPath.raw("$.b"))).containsExactly(false);
                assertThat(json.jsonMerge(key, JsonValue.raw("{\"n\":1}"))).isTrue();
                assertThat(json.jsonClear(key, JsonPath.raw("$.n"))).isEqualTo(1L);
                assertThat(json.jsonDel(key, JsonPath.raw("$.m"))).isEqualTo(1L);

                Awaitility.await().atMost(Durations.TEN_SECONDS).untilAsserted(() -> {
                    assertThat(new String(json.jsonGet(key), StandardCharsets.UTF_8)).isEqualTo("[{\"a\":[0,1],\"s\":\"xy\",\"b\":false,\"n\":0}]");
                });
                assertThat(json.jsonStrLen(key, JsonPath.raw("$.s"))).containsExactly(2L);
                assertThat(json.jsonMGet(key)).hasSize(1);
                assertThat(json.jsonType(key)).containsExactly(JsonType.OBJECT);
                assertThat(json.jsonArrLen(key, JsonPath.raw("$.a"))).containsExactly(2L);
                assertThat(json.jsonArrIndex(key, JsonPath.raw("$.a"), JsonValue.of(1))).containsExactly(1L);

                for (String command : Arrays.asList("json.get", "json.mget", "json.type", "json.strlen", "json.arrlen", "json.arrindex")) {
                    assertThat(calls(nodes.get(0), command)).as(command).isZero();
                    assertThat(calls(nodes.get(1), command)).as(command).isPositive();
                }
            } finally {
                client.shutdown();
            }
        }, 1);
    }

    private long calls(ContainerState node, String command) {
        String stats = execute(node, "redis-cli", "info", "commandstats");
        for (String line : stats.split("\n")) {
            if (line.startsWith("cmdstat_" + command + ":")) {
                Matcher matcher = CALLS.matcher(line);
                assertThat(matcher.find()).isTrue();
                return Long.parseLong(matcher.group(1));
            }
        }
        return 0;
    }

}
