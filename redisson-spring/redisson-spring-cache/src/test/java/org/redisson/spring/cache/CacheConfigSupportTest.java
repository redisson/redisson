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
package org.redisson.spring.cache;

import org.junit.jupiter.api.Test;
import org.redisson.api.map.event.MapEntryListener;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class CacheConfigSupportTest {

    private final CacheConfigSupport cacheConfigSupport = new CacheConfigSupport();

    public static class MapListener implements MapEntryListener {

    }

    @Test
    void testFromYAML() throws IOException {
        String yaml = "testMap:\n" +
                "  ttl: 1450000\n" +
                "  maxIdleTime: 721000\n";

        Map<String, CacheConfig> result = cacheConfigSupport.fromYAML(yaml);

        assertThat(result)
                .hasSize(1)
                .containsKey("testMap");

        CacheConfig config = result.get("testMap");
        assertThat(config)
                .isNotNull()
                .isInstanceOf(CacheConfig.class);
        assertThat(config.getTTL()).isEqualTo(1450000);
        assertThat(config.getMaxIdleTime()).isEqualTo(721000);
        assertThat(config.getMaxSize()).isEqualTo(0);
    }

    @Test
    void testFromYAMLListeners() throws IOException {
        String inputYaml =  "testMap:\n" +
                            "  ttl: 1450000\n" +
                            "  maxIdleTime: 721000\n" +
                            "  listeners:\n" +
                            "   - !<org.redisson.spring.cache.CacheConfigSupportTest$MapListener> {}";

        Map<String, CacheConfig> result = cacheConfigSupport.fromYAML(inputYaml);

        assertThat(result)
                .hasSize(1)
                .containsKey("testMap");

        CacheConfig config = result.get("testMap");
        assertThat(config)
                .isNotNull()
                .isInstanceOf(CacheConfig.class);
        assertThat(config.getTTL()).isEqualTo(1450000);
        assertThat(config.getMaxIdleTime()).isEqualTo(721000);
        assertThat(config.getMaxSize()).isEqualTo(0);
        assertThat(config.getListeners()).hasSize(1);
        for (MapEntryListener listener : config.getListeners()) {
            assertThat(listener).isInstanceOf(MapListener.class);
        }
    }

    @Test
    void testToYAML() throws IOException {
        String inputYaml = "testMap:\n" +
                "  ttl: 1450000\n" +
                "  maxIdleTime: 721000\n";
        Map<String, CacheConfig> configs = cacheConfigSupport.fromYAML(inputYaml);

        String result = cacheConfigSupport.toYAML(configs);

        String expected = "testMap:\n" +
                          "  ttl: 1450000\n" +
                          "  evictionMode: LRU\n" +
                          "  maxIdleTime: 721000\n" +
                          "  maxSize: 0";

        assertThat(result.trim()).isEqualTo(expected);
    }

    @Test
    void testMultipleCacheConfigs() throws IOException {
        String yaml = "cache1:\n" +
                "  ttl: 1000000\n" +
                "  maxIdleTime: 500000\n" +
                "cache2:\n" +
                "  ttl: 2000000\n" +
                "  maxIdleTime: 1000000";

        Map<String, CacheConfig> result = cacheConfigSupport.fromYAML(yaml);

        assertThat(result)
                .hasSize(2)
                .containsKeys("cache1", "cache2");
        assertThat(result.get("cache1").getTTL()).isEqualTo(1000000);
        assertThat(result.get("cache2").getTTL()).isEqualTo(2000000);
    }

}
