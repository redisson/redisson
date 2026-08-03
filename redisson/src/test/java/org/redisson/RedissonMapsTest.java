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
package org.redisson;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMap;
import org.redisson.api.RMaps;
import org.redisson.api.RMapsImport;
import org.redisson.api.RedissonClient;
import org.redisson.api.map.MapsImportArgs;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.redisson.config.ReadMode;
import org.testcontainers.containers.GenericContainer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RedissonMapsTest extends RedisDockerTest {

    @Test
    public void testSet() {
        RMaps<String, String> maps = stringMaps(redisson);
        maps.set(Map.of("user:1", Map.of("name", "alice", "age", "30"),
                        "user:2", Map.of("name", "bob", "age", "25")));

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "age", "25"));
    }

    @Test
    public void testSetReplacesWholeObject() {
        RMap<String, String> map = redisson.getMap("user:1", StringCodec.INSTANCE);
        map.put("name", "alice");
        map.put("removedField", "value");

        RMaps<String, String> maps = stringMaps(redisson);
        maps.set(Map.of("user:1", Map.of("name", "bob", "age", "25")));

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "bob", "age", "25"));
    }

    @Test
    public void testSetDifferentFields() {
        RMaps<String, String> maps = stringMaps(redisson);
        maps.set(Map.of("user:1", Map.of("name", "alice", "age", "30"),
                        "user:2", Map.of("name", "bob", "age", "25"),
                        "city:1", Map.of("title", "Riga"),
                        "city:2", Map.of("title", "Vilnius")));

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "age", "25"));
        assertThat(readMap("city:1")).isEqualTo(Map.of("title", "Riga"));
        assertThat(readMap("city:2")).isEqualTo(Map.of("title", "Vilnius"));
    }

    /**
     * Objects sharing the same fields are written through a single fieldset, so the order
     * of fields has to be defined by the fields themselves and not by the passed object.
     */
    @Test
    public void testSetIgnoresFieldsIterationOrder() {
        Map<String, String> straightOrder = new LinkedHashMap<>();
        straightOrder.put("name", "alice");
        straightOrder.put("age", "30");

        Map<String, String> reversedOrder = new LinkedHashMap<>();
        reversedOrder.put("age", "25");
        reversedOrder.put("name", "bob");

        RMaps<String, String> maps = stringMaps(redisson);
        maps.set(Map.of("user:1", straightOrder, "user:2", reversedOrder));

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "age", "25"));
    }

    @Test
    public void testSetBatchSize() {
        Map<String, Map<String, String>> maps = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            maps.put("user:" + i, Map.of("name", "user" + i, "age", String.valueOf(i)));
        }

        stringMaps(redisson).set(maps, 100);

        assertThat(readMap("user:0")).isEqualTo(Map.of("name", "user0", "age", "0"));
        assertThat(readMap("user:999")).isEqualTo(Map.of("name", "user999", "age", "999"));
        assertThat(redisson.getKeys().count()).isEqualTo(1000);
    }

    @Test
    public void testSetEmpty() {
        RMaps<String, String> maps = stringMaps(redisson);
        maps.set(Map.of());

        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testSetEmptyObject() {
        RMaps<String, String> maps = stringMaps(redisson);

        assertThatThrownBy(() -> maps.set(Map.of("user:1", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testImport() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.fields("name", "email", "age"));
        mapsImport.add("user:1", "alice", "alice@redisson.org", "30");
        mapsImport.add("user:2", List.of("bob", "bob@redisson.org", "25"));
        mapsImport.flush();

        assertThat(mapsImport.getImportedCount()).isEqualTo(2);
        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "email", "alice@redisson.org", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "email", "bob@redisson.org", "age", "25"));
    }

    @Test
    public void testImportKeepsDefinedFieldsOrder() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.fields("name", "age"));
        mapsImport.add("user:1", "alice", "30");
        mapsImport.flush();

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "age", "30"));
    }

    @Test
    public void testImportFlushesByBatchSize() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.<String>fields("name")
                                                                                            .batchSize(2));
        for (int i = 0; i < 5; i++) {
            mapsImport.add("user:" + i, "user" + i);
        }

        assertThat(mapsImport.getImportedCount()).isEqualTo(4);
        assertThat(redisson.getKeys().count()).isEqualTo(4);

        mapsImport.flush();

        assertThat(mapsImport.getImportedCount()).isEqualTo(5);
        assertThat(redisson.getKeys().count()).isEqualTo(5);
    }

    @Test
    public void testImportFlushOfEmptyBuffer() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.fields("name"));
        mapsImport.flush();

        assertThat(mapsImport.getImportedCount()).isZero();
        assertThat(redisson.getKeys().count()).isZero();
    }

    @Test
    public void testImportLargeAmount() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.fields("name", "age"));
        for (int i = 0; i < 10000; i++) {
            mapsImport.add("user:" + i, "user" + i, String.valueOf(i));
        }
        mapsImport.flush();

        assertThat(mapsImport.getImportedCount()).isEqualTo(10000);
        assertThat(redisson.getKeys().count()).isEqualTo(10000);
        assertThat(readMap("user:9999")).isEqualTo(Map.of("name", "user9999", "age", "9999"));
    }

    @Test
    public void testImportValuesAmountMismatch() {
        RMapsImport<String, String> mapsImport = stringMaps(redisson)
                                                            .createImport(MapsImportArgs.fields("name", "age"));

        assertThatThrownBy(() -> mapsImport.add("user:1", "alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testImportDuplicatedFields() {
        RMaps<String, String> maps = stringMaps(redisson);

        assertThatThrownBy(() -> maps.createImport(MapsImportArgs.fields("name", "age", "name")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDefaultCodec() {
        RMaps<String, Integer> maps = redisson.getMaps();
        maps.set(Map.of("user:1", Map.of("age", 30)));

        RMap<String, Integer> map = redisson.getMap("user:1");
        assertThat(map.get("age")).isEqualTo(30);
    }

    @Test
    public void testNameMapper() {
        Config config = redisson.getConfig();
        config.useSingleServer()
                .setNameMapper(new NameMapper() {
                    @Override
                    public String map(String name) {
                        return "test::" + name;
                    }

                    @Override
                    public String unmap(String name) {
                        return name.replace("test::", "");
                    }
                });

        RedissonClient client = Redisson.create(config);
        try {
            stringMaps(client)
                    .set(Map.of("user:1", Map.of("name", "alice")));

            RMap<String, String> mappedName = client.getMap("user:1", StringCodec.INSTANCE);
            assertThat(mappedName.readAllMap()).isEqualTo(Map.of("name", "alice"));

            RMap<String, String> rawName = redisson.getMap("test::user:1", StringCodec.INSTANCE);
            assertThat(rawName.readAllMap()).isEqualTo(Map.of("name", "alice"));
        } finally {
            client.shutdown();
        }
    }

    /**
     * Servers without HIMPORT support are handled by the fallback, which has to store
     * exactly the same data as the HIMPORT based path.
     */
    @Test
    public void testFallbackWritesSameData() {
        withFallbackClient(fallbackClient -> {
            Map<String, String> object = Map.of("name", "alice", "email", "alice@redisson.org", "age", "30");

            stringMaps(redisson).set(Map.of("himport:1", object));
            stringMaps(fallbackClient).set(Map.of("fallback:1", object));

            assertThat(readMap("fallback:1")).isEqualTo(readMap("himport:1"));
        });
    }

    @Test
    public void testFallbackReplacesWholeObject() {
        withFallbackClient(fallbackClient -> {
            RMap<String, String> map = redisson.getMap("user:1", StringCodec.INSTANCE);
            map.put("name", "alice");
            map.put("removedField", "value");

            stringMaps(fallbackClient).set(Map.of("user:1", Map.of("name", "bob")));

            assertThat(readMap("user:1")).isEqualTo(Map.of("name", "bob"));
        });
    }

    /**
     * A server which doesn't know HIMPORT is recognized by the error of the command itself,
     * after which every write of the instance goes through the fallback.
     */
    @Test
    public void testFallbackOnServerWithoutHashImport() {
        GenericContainer<?> redis = createContainerByImage("redis:6.2");
        redis.start();

        RedissonClient client = Redisson.create(createConfig(redis));
        try {
            assertThat(((Redisson) client).getServiceManager().isHashImportDisabled()).isFalse();

            RMaps<String, String> maps = stringMaps(client);
            maps.set(Map.of("user:1", Map.of("name", "alice", "age", "30")));

            assertThat(((Redisson) client).getServiceManager().isHashImportDisabled()).isTrue();

            RMap<String, String> map = client.getMap("user:1", StringCodec.INSTANCE);
            assertThat(map.readAllMap()).isEqualTo(Map.of("name", "alice", "age", "30"));

            maps.set(Map.of("user:1", Map.of("name", "bob")));
            assertThat(map.readAllMap()).isEqualTo(Map.of("name", "bob"));

            RMapsImport<String, String> mapsImport = maps.createImport(MapsImportArgs.fields("name", "age"));
            mapsImport.add("user:2", "carol", "35");
            mapsImport.flush();

            assertThat(mapsImport.getImportedCount()).isEqualTo(1);
            RMap<String, String> importedMap = client.getMap("user:2", StringCodec.INSTANCE);
            assertThat(importedMap.readAllMap()).isEqualTo(Map.of("name", "carol", "age", "35"));
        } finally {
            client.shutdown();
            redis.stop();
        }
    }

    /**
     * Objects of a portion are spread over the nodes owning their slots, so a fieldset
     * has to be declared on each of the involved nodes.
     */
    @Test
    public void testImportInCluster() {
        testInCluster(client -> {
            RMapsImport<String, String> mapsImport = stringMaps(client)
                                                            .createImport(MapsImportArgs.fields("name", "age"));
            for (int i = 0; i < 100; i++) {
                mapsImport.add("user:" + i, "user" + i, String.valueOf(i));
            }
            mapsImport.flush();

            assertThat(mapsImport.getImportedCount()).isEqualTo(100);
            assertClusterObjects(client, 100);
        });
    }

    @Test
    public void testSetInCluster() {
        testInCluster(client -> {
            Map<String, Map<String, String>> maps = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                maps.put("user:" + i, Map.of("name", "user" + i, "age", String.valueOf(i)));
            }

            stringMaps(client).set(maps);

            assertClusterObjects(client, 100);
        });
    }

    /**
     * Read commands are routed to replicas by default, so written objects are read back
     * through a client reading from masters to not depend on the replication lag.
     */
    private void assertClusterObjects(RedissonClient client, int amount) {
        Config config = new Config(client.getConfig());
        config.useClusterServers().setReadMode(ReadMode.MASTER);

        RedissonClient masterReader = Redisson.create(config);
        try {
            for (int i = 0; i < amount; i++) {
                RMap<String, String> map = masterReader.getMap("user:" + i, StringCodec.INSTANCE);
                assertThat(map.readAllMap()).isEqualTo(Map.of("name", "user" + i, "age", String.valueOf(i)));
            }
        } finally {
            masterReader.shutdown();
        }
    }

    private void withFallbackClient(Consumer<RedissonClient> callback) {
        RedissonClient client = createInstance();
        ((Redisson) client).getServiceManager().disableHashImport();
        try {
            callback.accept(client);
        } finally {
            client.shutdown();
        }
    }

    private static RMaps<String, String> stringMaps(RedissonClient client) {
        return client.getMaps(StringCodec.INSTANCE);
    }

    private Map<String, String> readMap(String name) {
        RMap<String, String> map = redisson.getMap(name, StringCodec.INSTANCE);
        return map.readAllMap();
    }

}
