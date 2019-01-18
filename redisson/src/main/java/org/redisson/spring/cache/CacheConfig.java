/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

/**
 * Cache config object used for Spring cache configuration.
 *
 * @author Nikita Koksharov
 *
 */
public class CacheConfig {

    private long ttl;

    private long maxIdleTime;
    
    private int maxSize;

    /**
     * Creates config object with
     * <code>ttl = 0</code> and <code>maxIdleTime = 0</code>.
     *
     */
    public CacheConfig() {
    }

    /**
     * Creates config object.
     *
     * @param ttl - time to live for key\value entry in milliseconds.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param maxIdleTime - max idle time for key\value entry in milliseconds.
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     */
    public CacheConfig(long ttl, long maxIdleTime) {
        super();
        this.ttl = ttl;
        this.maxIdleTime = maxIdleTime;
    }

    public long getTTL() {
        return ttl;
    }

    /**
     * Set time to live for key\value entry in milliseconds.
     *
     * @param ttl - time to live for key\value entry in milliseconds.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     */
    public void setTTL(long ttl) {
        this.ttl = ttl;
    }

    
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Set max size of map. Superfluous elements are evicted using LRU algorithm.
     *
     * @param maxSize - max size
     *                  If <code>0</code> the cache is unbounded (default).
     */
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Set max idle time for key\value entry in milliseconds.
     *
     * @param maxIdleTime - max idle time for key\value entry in milliseconds.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     */
    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    /**
     * Read config objects stored in JSON format from <code>String</code>
     *
     * @param content of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromJSON(String content) throws IOException {
        return new CacheConfigSupport().fromJSON(content);
    }

    /**
     * Read config objects stored in JSON format from <code>InputStream</code>
     *
     * @param inputStream of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromJSON(InputStream inputStream) throws IOException {
        return new CacheConfigSupport().fromJSON(inputStream);
    }

    /**
     * Read config objects stored in JSON format from <code>File</code>
     *
     * @param file of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromJSON(File file) throws IOException {
        return new CacheConfigSupport().fromJSON(file);
    }

    /**
     * Read config objects stored in JSON format from <code>URL</code>
     *
     * @param url of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromJSON(URL url) throws IOException {
        return new CacheConfigSupport().fromJSON(url);
    }

    /**
     * Read config objects stored in JSON format from <code>Reader</code>
     *
     * @param reader of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromJSON(Reader reader) throws IOException {
        return new CacheConfigSupport().fromJSON(reader);
    }

    /**
     * Convert current configuration to JSON format
     *
     * @param config object
     * @return json string
     * @throws IOException error
     */
    public static String toJSON(Map<String, ? extends CacheConfig> config) throws IOException {
        return new CacheConfigSupport().toJSON(config);
    }

    /**
     * Read config objects stored in YAML format from <code>String</code>
     *
     * @param content of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromYAML(String content) throws IOException {
        return new CacheConfigSupport().fromYAML(content);
    }

    /**
     * Read config objects stored in YAML format from <code>InputStream</code>
     *
     * @param inputStream of config
     * @return config
     * @throws IOException  error
     */
    public static Map<String, ? extends CacheConfig> fromYAML(InputStream inputStream) throws IOException {
        return new CacheConfigSupport().fromYAML(inputStream);
    }

    /**
     * Read config objects stored in YAML format from <code>File</code>
     *
     * @param file of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromYAML(File file) throws IOException {
        return new CacheConfigSupport().fromYAML(file);
    }

    /**
     * Read config objects stored in YAML format from <code>URL</code>
     *
     * @param url of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromYAML(URL url) throws IOException {
        return new CacheConfigSupport().fromYAML(url);
    }

    /**
     * Read config objects stored in YAML format from <code>Reader</code>
     *
     * @param reader of config
     * @return config
     * @throws IOException error
     */
    public static Map<String, ? extends CacheConfig> fromYAML(Reader reader) throws IOException {
        return new CacheConfigSupport().fromYAML(reader);
    }

    /**
     * Convert current configuration to YAML format
     *
     * @param config map
     * @return yaml string
     * @throws IOException error
     */
    public static String toYAML(Map<String, ? extends CacheConfig> config) throws IOException {
        return new CacheConfigSupport().toYAML(config);
    }

}
