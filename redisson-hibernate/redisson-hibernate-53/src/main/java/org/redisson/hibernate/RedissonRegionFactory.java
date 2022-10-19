/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.CacheKeysFactory;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.support.*;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.redisson.Redisson;
import org.redisson.api.RMapCache;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.config.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * Hibernate Cache region factory based on Redisson. 
 * Creates own Redisson instance during region start.
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonRegionFactory extends RegionFactoryTemplate {
    
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

    private RedissonClient redisson;
    private CacheKeysFactory cacheKeysFactory;
    protected boolean fallback;

    @Override
    protected CacheKeysFactory getImplicitCacheKeysFactory() {
        return cacheKeysFactory;
    }

    @Override
    protected void prepareForUse(SessionFactoryOptions settings, @SuppressWarnings("rawtypes") Map properties) throws CacheException {
        this.redisson = createRedissonClient(properties);

        String fallbackValue = (String) properties.getOrDefault(FALLBACK, "false");
        fallback = Boolean.valueOf(fallbackValue);

        StrategySelector selector = settings.getServiceRegistry().getService(StrategySelector.class);
        cacheKeysFactory = selector.resolveDefaultableStrategy(CacheKeysFactory.class, 
                properties.get(Environment.CACHE_KEYS_FACTORY), new RedissonCacheKeysFactory(redisson.getConfig().getCodec()));
    }

    protected RedissonClient createRedissonClient(Map properties) {
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
        } catch (IOException e) {
            // trying next format
            try {
                return Config.fromJSON(new File(configPath));
            } catch (IOException e1) {
                throw new CacheException("Can't parse default yaml config", e1);
            }
        }
    }
    
    private Config loadConfig(ClassLoader classLoader, String fileName) {
        InputStream is = classLoader.getResourceAsStream(fileName);
        if (is != null) {
            try {
                return Config.fromYAML(is);
            } catch (IOException e) {
                try {
                    is = classLoader.getResourceAsStream(fileName);
                    return Config.fromJSON(is);
                } catch (IOException e1) {
                    throw new CacheException("Can't parse yaml config", e1);
                }
            }
        }
        return null;
    }

    @Override
    protected void releaseFromUse() {
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
                    RScript.ReturnType.INTEGER, Arrays.<Object>asList("redisson-hibernate-timestamp"), time);
        } catch (Exception e) {
            if (fallback) {
                return super.nextTimestamp();
            }
            throw e;
        }
    }

    @Override
    public DomainDataRegion buildDomainDataRegion(
            DomainDataRegionConfig regionConfig,
            DomainDataRegionBuildingContext buildingContext) {
        verifyStarted();
        return new DomainDataRegionImpl(
                regionConfig,
                this,
                createDomainDataStorageAccess( regionConfig, buildingContext ),
                getImplicitCacheKeysFactory(),
                buildingContext
        );
    }

    @Override
    protected DomainDataStorageAccess createDomainDataStorageAccess(DomainDataRegionConfig regionConfig,
            DomainDataRegionBuildingContext buildingContext) {
        String defaultKey = null;
        if (!regionConfig.getCollectionCaching().isEmpty()) {
            defaultKey = COLLECTION_DEF;
        } else if (!regionConfig.getEntityCaching().isEmpty()) {
            defaultKey = ENTITY_DEF;
        } else if (!regionConfig.getNaturalIdCaching().isEmpty()) {
            defaultKey = NATURAL_ID_DEF;
        } else {
            throw new IllegalArgumentException("Unable to determine entity cache type!");
        }

        RMapCache<Object, Object> mapCache = getCache(qualifyName(regionConfig.getRegionName()), buildingContext.getSessionFactory().getProperties(), defaultKey);
        return new RedissonStorage(mapCache, ((Redisson)redisson).getConnectionManager(), buildingContext.getSessionFactory().getProperties(), defaultKey);
    }

    private String qualifyName(String name) {
        return RegionNameQualifier.INSTANCE.qualify(name, getOptions());
    }

    @Override
    protected StorageAccess createQueryResultsRegionStorageAccess(String regionName,
            SessionFactoryImplementor sessionFactory) {
        RMapCache<Object, Object> mapCache = getCache(qualifyName(regionName), sessionFactory.getProperties(), QUERY_DEF);
        return new RedissonStorage(mapCache, ((Redisson)redisson).getConnectionManager(), sessionFactory.getProperties(), QUERY_DEF);
    }

    @Override
    protected StorageAccess createTimestampsRegionStorageAccess(String regionName,
            SessionFactoryImplementor sessionFactory) {
        RMapCache<Object, Object> mapCache = getCache(qualifyName(regionName), sessionFactory.getProperties(), TIMESTAMPS_DEF);
        return new RedissonStorage(mapCache, ((Redisson)redisson).getConnectionManager(), sessionFactory.getProperties(), TIMESTAMPS_DEF);
    }

    protected RMapCache<Object, Object> getCache(String regionName, Map properties, String defaultKey) {
        return redisson.getMapCache(regionName);
    }
    
}
