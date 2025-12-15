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
package org.redisson.hibernate;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.Settings;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.jboss.logging.Logger;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.config.Config;
import org.redisson.hibernate.region.*;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hibernate Cache region factory based on Redisson. 
 * Creates own Redisson instance during region start.
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRegionFactory implements RegionFactory {
    
    private static final Logger log = Logger.getLogger( RedissonRegionFactory.class );

    private static final long serialVersionUID = 3785315696581773811L;

    public static final String QUERY_DEF = "query";
    
    public static final String COLLECTION_DEF = "collection";
    
    public static final String ENTITY_DEF = "entity";
    
    public static final String NATURAL_ID_DEF = "naturalid";
    
    public static final String TIMESTAMPS_DEF = "timestamps";
    
    public static final String MAX_ENTRIES_SUFFIX = ".eviction.max_entries";

    public static final String TTL_SUFFIX = ".expiration.time_to_live";

    public static final String MAX_IDLE_SUFFIX = ".expiration.max_idle_time";
    
    public static final String CONFIG_PREFIX = "hibernate.cache.redisson.";
    
    public static final String REDISSON_CONFIG_PATH = CONFIG_PREFIX + "config";

    public static final String FALLBACK = CONFIG_PREFIX + "fallback";

    private static AtomicLong currentTime = new AtomicLong();

    protected RedissonClient redisson;
    private Settings settings;
    private boolean fallback;

    @Override
    public void start(Settings settings, Properties properties) throws CacheException {
        this.redisson = createRedissonClient(properties);
        this.settings = settings;

        String fallbackValue = (String) properties.getOrDefault(FALLBACK, "false");
        fallback = Boolean.valueOf(fallbackValue);
    }

    protected RedissonClient createRedissonClient(Properties properties) {
        Config config = null;
        if (!properties.containsKey(REDISSON_CONFIG_PATH)) {
            config = loadConfig(RedissonRegionFactory.class.getClassLoader(), "redisson.json");
            if (config == null) {
                config = loadConfig(RedissonRegionFactory.class.getClassLoader(), "redisson.yaml");
            }
        } else {
            String configPath = ConfigurationHelper.getString(REDISSON_CONFIG_PATH, properties);
            config = loadConfig(RedissonRegionFactory.class.getClassLoader(), configPath);
            if (config == null) {
                config = loadConfig(configPath);
            }
        }
        
        if (config == null) {
            throw new CacheException("Unable to locate Redisson configuration");
        }
        
        return Redisson.create(config);
    }

    private Config loadConfig(String configPath) {
        try {
            return Config.fromYAML(new File(configPath));
        } catch (Exception e) {
            throw new CacheException("Can't parse default config", e);
        }
    }
    
    private Config loadConfig(ClassLoader classLoader, String fileName) {
        InputStream is = classLoader.getResourceAsStream(fileName);
        if (is != null) {
            try {
                return Config.fromYAML(is);
            } catch (Exception e) {
                throw new CacheException("Can't parse config", e);
            }
        }
        return null;
    }

    @Override
    public void stop() {
        redisson.shutdown();
    }

    @Override
    public boolean isMinimalPutsEnabledByDefault() {
        return true;
    }

    @Override
    public AccessType getDefaultAccessType() {
        return AccessType.TRANSACTIONAL;
    }

    @Override
    public long nextTimestamp() {
        long time = System.currentTimeMillis() << 12;
        try {
            return redisson.getScript(LongCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                      "local currentTime = redis.call('get', KEYS[1]);"
                            + "if currentTime == false then "
                                + "redis.call('set', KEYS[1], ARGV[1]); "
                                + "return ARGV[1]; "
                            + "end;"
                            + "local nextValue = math.max(tonumber(ARGV[1]), tonumber(currentTime) + 1); "
                            + "redis.call('set', KEYS[1], nextValue); "
                            + "return nextValue;",
                            RScript.ReturnType.LONG, Collections.singletonList("redisson-hibernate-timestamp"), time);
        } catch (Exception e) {
            if (fallback) {
                while (true) {
                    if (currentTime.get() == 0) {
                        if (currentTime.compareAndSet(0, time)) {
                            return time;
                        }
                    } else {
                        long currValue = currentTime.get();
                        long nextTime = Math.max(time, currValue + 1);
                        if (currentTime.compareAndSet(currValue, nextTime)) {
                            return nextTime;
                        }
                    }
                }
            }
            throw e;
        }
    }

    @Override
    public EntityRegion buildEntityRegion(String regionName, Properties properties, CacheDataDescription metadata)
            throws CacheException {
        log.debug("Building entity cache region: " + regionName);

        RMapCache<Object, Object> mapCache = getCache(regionName, properties, ENTITY_DEF);
        return new RedissonEntityRegion(mapCache, ((Redisson)redisson).getServiceManager(),this, metadata, settings, properties, ENTITY_DEF);
    }

    @Override
    public NaturalIdRegion buildNaturalIdRegion(String regionName, Properties properties, CacheDataDescription metadata)
            throws CacheException {
        log.debug("Building naturalId cache region: " + regionName);
        
        RMapCache<Object, Object> mapCache = getCache(regionName, properties, NATURAL_ID_DEF);
        return new RedissonNaturalIdRegion(mapCache, ((Redisson)redisson).getServiceManager(),this, metadata, settings, properties, NATURAL_ID_DEF);
    }

    @Override
    public CollectionRegion buildCollectionRegion(String regionName, Properties properties,
            CacheDataDescription metadata) throws CacheException {
        log.debug("Building collection cache region: " + regionName);
        
        RMapCache<Object, Object> mapCache = getCache(regionName, properties, COLLECTION_DEF);
        return new RedissonCollectionRegion(mapCache, ((Redisson)redisson).getServiceManager(),this, metadata, settings, properties, COLLECTION_DEF);
    }

    @Override
    public QueryResultsRegion buildQueryResultsRegion(String regionName, Properties properties) throws CacheException {
        log.debug("Building query cache region: " + regionName);
        
        RMapCache<Object, Object> mapCache = getCache(regionName, properties, QUERY_DEF);
        return new RedissonQueryRegion(mapCache, ((Redisson)redisson).getServiceManager(),this, properties, QUERY_DEF);
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(String regionName, Properties properties) throws CacheException {
        log.debug("Building timestamps cache region: " + regionName);
        
        RMapCache<Object, Object> mapCache = getCache(regionName, properties, TIMESTAMPS_DEF);
        return new RedissonTimestampsRegion(mapCache, ((Redisson)redisson).getServiceManager(),this, properties, TIMESTAMPS_DEF);
    }

    protected RMapCache<Object, Object> getCache(String regionName, Properties properties, String defaultKey) {
        return redisson.getMapCache(regionName);
    }
    
}
