package org.redisson.jnosql;

import org.eclipse.jnosql.communication.Value;
import org.eclipse.jnosql.communication.keyvalue.KeyValueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.awaitility.Awaitility.await;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBucketManagerTest extends BaseTest {

    private RedissonBucketManager manager;

    @BeforeEach
    void createManager() {
        manager = new RedissonKeyValueConfiguration().apply(redisson).apply("books");
    }

    @Test
    public void testPutAndGet() {
        Book dune = new Book("9780441013593", "Dune", 1965);
        manager.put(dune.getIsbn(), dune);
        manager.put(KeyValueEntity.of("title", "Dune"));
        manager.put(42, 7L);

        assertThat(manager.get("9780441013593").map(v -> v.get(Book.class)))
                .get().usingRecursiveComparison().isEqualTo(dune);
        assertThat(manager.get("title").map(v -> v.get(String.class))).contains("Dune");
        assertThat(manager.get(42).map(v -> v.get(Long.class))).contains(7L);
        assertThat(manager.get("missing")).isEmpty();
        assertThat(manager.name()).isEqualTo("books");
    }

    @Test
    public void testStorageFormat() {
        manager.put("9780441013593", new Book("9780441013593", "Dune", 1965));
        manager.put("title", "Dune");

        assertThat(redisson.getKeys().getKeys()).containsExactlyInAnyOrder("books:9780441013593", "books:title");
        assertThat(redisson.getBucket("books:9780441013593", StringCodec.INSTANCE).get())
                .isEqualTo("{\"isbn\":\"9780441013593\",\"title\":\"Dune\",\"year\":1965}");
        assertThat(redisson.getBucket("books:title", StringCodec.INSTANCE).get()).isEqualTo("\"Dune\"");
    }

    @Test
    public void testEmptyValueIsAbsent() {
        redisson.getBucket("books:1", StringCodec.INSTANCE).set("");

        assertThat(manager.get("1")).isEmpty();
        assertThat(manager.get(List.of("1"))).isEmpty();
    }

    @Test
    public void testPutWithTtl() {
        manager.put(KeyValueEntity.of("1", "a"), Duration.ofSeconds(10));

        assertThat(redisson.getBucket("books:1").remainTimeToLive()).isBetween(1L, 10000L);
        assertThat(manager.get("1").map(v -> v.get(String.class))).contains("a");
    }

    @Test
    public void testPutWithTtlBelowMillisecond() {
        manager.put(KeyValueEntity.of("1", "a"), Duration.ofNanos(1));

        await().atMost(Duration.ofSeconds(1)).until(() -> manager.get("1").isEmpty());
    }

    @Test
    public void testPutAll() {
        manager.put(List.of(KeyValueEntity.of("1", "a"), KeyValueEntity.of("2", "b"), KeyValueEntity.of("1", "c")));

        assertThat(manager.get("1").map(v -> v.get(String.class))).contains("c");
        assertThat(manager.get("2").map(v -> v.get(String.class))).contains("b");
        assertThat(redisson.getKeys().count()).isEqualTo(2);
        assertThat(redisson.getBucket("books:1").remainTimeToLive()).isEqualTo(-1);
    }

    @Test
    public void testPutAllWithTtl() {
        manager.put(List.of(KeyValueEntity.of("1", "a"), KeyValueEntity.of("2", "b")), Duration.ofSeconds(10));

        assertThat(redisson.getBucket("books:1").remainTimeToLive()).isBetween(1L, 10000L);
        assertThat(redisson.getBucket("books:2").remainTimeToLive()).isBetween(1L, 10000L);
        assertThat(manager.get("2").map(v -> v.get(String.class))).contains("b");
    }

    @Test
    public void testPutAllOfNothing() {
        manager.put(List.of());
        manager.put(List.of(), Duration.ofSeconds(10));

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testNonPositiveTtl() {
        KeyValueEntity entity = KeyValueEntity.of("1", "a");

        assertThatIllegalArgumentException().isThrownBy(() -> manager.put(entity, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> manager.put(entity, Duration.ofMillis(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> manager.put(List.of(entity), Duration.ZERO));
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testGetAll() {
        manager.put("1", "a");
        manager.put("2", "b");

        Iterable<Value> values = manager.get(List.of("2", "missing", "1", "2"));

        assertThat(StreamSupport.stream(values.spliterator(), false).map(v -> v.get(String.class)))
                .containsExactly("b", "a", "b");
        assertThat(manager.get(List.<String>of())).isEmpty();
    }

    @Test
    public void testDelete() {
        manager.put("1", "a");
        manager.put("2", "b");
        manager.put("3", "c");
        manager.put("4", "d");

        manager.delete("1");
        manager.delete(List.of("2", "missing", "3"));
        manager.delete(List.<String>of());

        assertThat(redisson.getKeys().getKeys()).containsExactly("books:4");
    }

    @Test
    public void testNullArguments() {
        KeyValueEntity entity = KeyValueEntity.of("1", "a");
        List<KeyValueEntity> nullEntity = Arrays.asList(entity, null);
        List<String> nullKey = Arrays.asList("1", null);

        assertThatNullPointerException().isThrownBy(() -> manager.put(null, "a"));
        assertThatNullPointerException().isThrownBy(() -> manager.put("1", null));
        assertThatNullPointerException().isThrownBy(() -> manager.put((KeyValueEntity) null));
        assertThatNullPointerException().isThrownBy(() -> manager.put((KeyValueEntity) null, Duration.ofSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> manager.put(entity, null));
        assertThatNullPointerException().isThrownBy(() -> manager.put((Iterable<KeyValueEntity>) null));
        assertThatNullPointerException().isThrownBy(() -> manager.put(nullEntity));
        assertThatNullPointerException().isThrownBy(() -> manager.put(nullEntity, Duration.ofSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> manager.put(List.of(entity), null));
        assertThatNullPointerException().isThrownBy(() -> manager.get((Object) null));
        assertThatNullPointerException().isThrownBy(() -> manager.get((Iterable<String>) null));
        assertThatNullPointerException().isThrownBy(() -> manager.get(nullKey));
        assertThatNullPointerException().isThrownBy(() -> manager.delete((Object) null));
        assertThatNullPointerException().isThrownBy(() -> manager.delete((Iterable<String>) null));
        assertThatNullPointerException().isThrownBy(() -> manager.delete(nullKey));
        assertThat(redisson.getKeys().count()).isZero();
    }

}
