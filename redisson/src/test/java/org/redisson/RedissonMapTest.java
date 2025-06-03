package org.redisson;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.junit.jupiter.api.Test;
import org.redisson.api.AsyncIterator;
import org.redisson.api.MapOptions;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.RMap;
import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapTest extends BaseMapTest {

    @Test
    public void testAddAndGetMapWriter() {
        Map<Long, Long> storage = new HashMap<>();
        storage.put(1L, 1L);
        storage.put(2L, 2L);
        storage.put(3L, 3L);

        RMap<Long, Long> map = getWriteBehindAsyncTestMap("test", storage);

        map.addAndGet(1L, 10L);
        Awaitility.waitAtMost(Durations.TWO_SECONDS).until(() -> storage.get(1L).equals(10L));
    }

    protected <K, V> MapWriter<K, V> createMapWriter(Map<K, V> map) {
        return new MapWriter<K, V>() {

            @Override
            public void write(Map<K, V> values) {
                map.putAll(values);
                System.out.println("map " + map);
            }

            @Override
            public void delete(Collection<K> keys) {
                for (K key : keys) {
                    map.remove(key);
                }
                System.out.println("delete " + keys + " map " + map);
            }

        };
    }

    protected <K, V> MapLoader<K, V> createMapLoader(Map<K, V> map) {
        return new MapLoader<K, V>() {
            @Override
            public V load(K key) {
                return map.get(key);
            }

            @Override
            public Iterable<K> loadAllKeys() {
                return map.keySet();
            }
        };
    }

    protected <K, V> RMap<K, V> getWriteBehindTestMap2(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults()
                .writer(createMapWriter(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMap(name, LongCodec.INSTANCE, options);
    }

        @Override
    protected <K, V> RMap<K, V> getMap(String name) {
        return redisson.getMap(name);
    }

        @Override
    protected <K, V> RMap<K, V> getMap(String name, Codec codec) {
        return redisson.getMap(name, codec);
    }

    @Override
    protected <K, V, M extends RMap<K, V>> M getLoaderTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().loader(createMapLoader(map));
        return (M) redisson.getMap("test", options);
    }

    @Override
    protected <K, V> RMap<K, V> getLoaderAsyncTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().loaderAsync(createMapLoaderAsync(map));
        return redisson.getMap("test", options);
    }

    @Override
    protected <K, V> RMap<K, V> getWriterTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults().writer(createMapWriter(map));
        return redisson.getMap("test", options);        
    }
    
    @Override
    protected <K, V> RMap<K, V> getWriteBehindTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults()
                                    .writer(createMapWriter(map))
                                    .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMap("test", options);        
    }

    @Override
    protected <K, V> RMap<K, V> getWriteBehindAsyncTestMap(String name, Map<K, V> map) {
        MapOptions<K, V> options = MapOptions.<K, V>defaults()
                .writerAsync(createMapWriterAsync(map))
                .writeMode(WriteMode.WRITE_BEHIND);
        return redisson.getMap("test", options);
    }

    @Test
    public void testEntrySet() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.entrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.entrySet()).containsExactlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testValuesAsync() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        List<String> list = new ArrayList<>();
        AsyncIterator<String> iterator = map.valuesAsync();
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();

        assertThat(map.size()).isEqualTo(list.size());

    }

    @Test
    public void testValuesByCountAsync() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        List<String> list = new ArrayList<>();
        AsyncIterator<String> iterator = map.valuesAsync(2);
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();

        assertThat(list.size()).isEqualTo(3);

    }

    @Test
    public void testValuesByPatternAsync() {
        RMap<String, String> map = getMap("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200");
        map.put("30", "300");

        List<String> list = new ArrayList<>();

        AsyncIterator<String> iterator = map.valuesAsync("?0");
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();
        assertThat(list).containsExactlyInAnyOrder("100", "200", "300");

        list.clear();
        AsyncIterator<String> iterator2 = map.valuesAsync("1");
        CompletionStage<Void> f2 = iterateAll(iterator2, list);
        f2.toCompletableFuture().join();
        assertThat(list.isEmpty()).isTrue();

        list.clear();
        AsyncIterator<String> iterator3 = map.valuesAsync("10");
        CompletionStage<Void> f3 = iterateAll(iterator3, list);
        f3.toCompletableFuture().join();
        assertThat(list).containsExactlyInAnyOrder("100");

    }

    @Test
    public void testEntrySetByCountAsync() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        List<java.util.Map.Entry<Integer, String>> list = new ArrayList<>();
        AsyncIterator<java.util.Map.Entry<Integer, String>> iterator = map.entrySetAsync(2);
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();

        assertThat(list.size()).isEqualTo(3);

    }

    @Test
    public void testEntrySetAsync() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        List<java.util.Map.Entry<Integer, String>> list = new ArrayList<>();
        AsyncIterator<java.util.Map.Entry<Integer, String>> iterator = map.entrySetAsync();
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();

        assertThat(map.size()).isEqualTo(list.size());

    }

    @Test
    public void testVEntrySetByPatternAsync() {
        RMap<String, String> map = getMap("simple", StringCodec.INSTANCE);
        map.put("10", "100");
        map.put("20", "200");
        map.put("30", "300");

        List<java.util.Map.Entry<String, String>> list = new ArrayList<>();

        AsyncIterator<java.util.Map.Entry<String, String>> iterator = map.entrySetAsync("?0");
        CompletionStage<Void> f = iterateAll(iterator, list);
        f.toCompletableFuture().join();
        assertThat(list.size()).isEqualTo(3);

        list.clear();
        AsyncIterator<java.util.Map.Entry<String, String>> iterator2 = map.entrySetAsync("1");
        CompletionStage<Void> f2 = iterateAll(iterator2, list);
        f2.toCompletableFuture().join();
        assertThat(list.isEmpty()).isTrue();

        list.clear();
        AsyncIterator<java.util.Map.Entry<String, String>> iterator3 = map.entrySetAsync("10");
        CompletionStage<Void> f3 = iterateAll(iterator3, list);
        f3.toCompletableFuture().join();
        assertThat(list.size()).isEqualTo(1);

    }

    public CompletionStage<Void> iterateAll(AsyncIterator<?> iterator, List list) {
        return iterator.hasNext().thenCompose(r -> {
            if (r) {
                return iterator.next().thenCompose(k -> {
                    list.add(k);
                    return iterateAll(iterator, list);
                });
            } else {
                return CompletableFuture.completedFuture(null);
            }
        });
    }

    @Test
    public void testReadAllEntrySet() {
        RMap<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        assertThat(map.readAllEntrySet().size()).isEqualTo(3);
        Map<Integer, String> testMap = new HashMap<Integer, String>(map);
        assertThat(map.readAllEntrySet()).containsExactlyElementsOf(testMap.entrySet());
    }

    @Test
    public void testReadAllEntrySetWithPattern() throws ExecutionException, InterruptedException {
        RMap<String, String> map = redisson.getMap("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33");
        map.put("21", "43");

        assertThat((map.readAllEntrySetAsync("1?")).get().size()).isEqualTo(2);
    }

    @Test
    public void testReadAllKeySetWithPattern() throws ExecutionException, InterruptedException {
        RMap<String, String> map = redisson.getMap("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33");
        map.put("21", "43");

        assertThat((map.readAllKeySetAsync("1?")).get()).containsOnly("10", "12");
    }

    @Test
    public void testReadAllValuesWithPattern() throws ExecutionException, InterruptedException {
        RMap<String, String> map = redisson.getMap("simple12", StringCodec.INSTANCE);
        map.put("10", "12");
        map.put("12", "33");
        map.put("21", "43");

        assertThat((map.readAllValuesAsync("1?")).get()).containsOnly("12", "33");
    }

    @Test
    public void testSimpleTypes() {
        Map<Integer, String> map = redisson.getMap("simple12");
        map.put(1, "12");
        map.put(2, "33");
        map.put(3, "43");

        String val = map.get(2);
        assertThat(val).isEqualTo("33");
    }

    @Test
    public void testKeySet() {
        Map<SimpleKey, SimpleValue> map = redisson.getMap("simple");
        map.put(new SimpleKey("1"), new SimpleValue("2"));
        map.put(new SimpleKey("33"), new SimpleValue("44"));
        map.put(new SimpleKey("5"), new SimpleValue("6"));

        assertThat(map.keySet()).containsOnly(new SimpleKey("33"), new SimpleKey("1"), new SimpleKey("5"));
    }
    
    @Test
    public void testKeyIterator() {
        RMap<Integer, Integer> map = redisson.getMap("simple");
        map.put(1, 0);
        map.put(3, 5);
        map.put(4, 6);
        map.put(7, 8);

        Collection<Integer> keys = map.keySet();
        assertThat(keys).containsOnly(1, 3, 4, 7);
        for (Iterator<Integer> iterator = map.keySet().iterator(); iterator.hasNext();) {
            Integer value = iterator.next();
            if (!keys.remove(value)) {
                Assertions.fail("value can't be removed");
            }
        }

        assertThat(keys.size()).isEqualTo(0);
    }

            }
