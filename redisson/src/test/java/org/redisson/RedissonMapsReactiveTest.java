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
import org.redisson.api.RMapReactive;
import org.redisson.api.RMapsImportReactive;
import org.redisson.api.RMapsReactive;
import org.redisson.api.map.MapsImportArgs;
import org.redisson.client.codec.StringCodec;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonMapsReactiveTest extends BaseReactiveTest {

    @Test
    public void testSet() {
        RMapsReactive<String, String> maps = redisson.getMaps(StringCodec.INSTANCE);
        sync(maps.set(Map.of("user:1", Map.of("name", "alice", "age", "30"),
                                "user:2", Map.of("name", "bob", "age", "25"))));

        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "age", "25"));
    }

    @Test
    public void testImport() {
        RMapsImportReactive<String, String> mapsImport = createImport("name", "email", "age");

        sync(mapsImport.add("user:1", "alice", "alice@redisson.org", "30"));
        sync(mapsImport.add("user:2", List.of("bob", "bob@redisson.org", "25")));
        sync(mapsImport.flush());

        assertThat(mapsImport.getImportedCount()).isEqualTo(2);
        assertThat(readMap("user:1")).isEqualTo(Map.of("name", "alice", "email", "alice@redisson.org", "age", "30"));
        assertThat(readMap("user:2")).isEqualTo(Map.of("name", "bob", "email", "bob@redisson.org", "age", "25"));
    }

    /**
     * Objects are buffered on subscription, so an import object composed into a stream
     * writes the whole stream and not only the objects added before the subscription.
     */
    @Test
    public void testImportComposedIntoStream() {
        RMapsImportReactive<String, String> mapsImport =
                redisson.<String, String>getMaps(StringCodec.INSTANCE)
                        .createImport(MapsImportArgs.<String>fields("name", "age").batchSize(10));

        Flux.range(0, 100)
                .concatMap(i -> mapsImport.add("user:" + i, "user" + i, String.valueOf(i)))
                .then(mapsImport.flush())
                .block();

        assertThat(mapsImport.getImportedCount()).isEqualTo(100);
        assertThat(readMap("user:0")).isEqualTo(Map.of("name", "user0", "age", "0"));
        assertThat(readMap("user:99")).isEqualTo(Map.of("name", "user99", "age", "99"));
    }

    @Test
    public void testAddIsAppliedOnSubscription() {
        RMapsImportReactive<String, String> mapsImport = createImport("name");
        mapsImport.add("user:1", "alice");

        sync(mapsImport.flush());

        assertThat(mapsImport.getImportedCount()).isZero();
        assertThat(sync(redisson.getKeys().count())).isZero();
    }

    private RMapsImportReactive<String, String> createImport(String... fields) {
        return redisson.<String, String>getMaps(StringCodec.INSTANCE)
                        .createImport(MapsImportArgs.fields(fields));
    }

    private Map<String, String> readMap(String name) {
        RMapReactive<String, String> map = redisson.getMap(name, StringCodec.INSTANCE);
        return sync(map.readAllMap());
    }

}
