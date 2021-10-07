/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.micronaut.session;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.Toggleable;
import io.micronaut.session.http.HttpSessionConfiguration;
import org.redisson.client.codec.Codec;

/**
 * Micronaut Session settings.
 *
 * @author Nikita Koksharov
 */
@ConfigurationProperties("redisson")
public class RedissonHttpSessionConfiguration extends HttpSessionConfiguration implements Toggleable {

    public enum UpdateMode {WRITE_BEHIND, AFTER_REQUEST}

    private String keyPrefix = "";
    private Codec codec;
    private UpdateMode updateMode = UpdateMode.AFTER_REQUEST;
    private boolean broadcastSessionUpdates = false;

    public boolean isBroadcastSessionUpdates() {
        return broadcastSessionUpdates;
    }

    /**
     * Defines broadcasting of session updates across all micronaut services.
     *
     * @param broadcastSessionUpdates - if true then session changes are broadcasted.
     */
    public void setBroadcastSessionUpdates(boolean broadcastSessionUpdates) {
        this.broadcastSessionUpdates = broadcastSessionUpdates;
    }

    public UpdateMode getUpdateMode() {
        return updateMode;
    }

    /**
     * Defines session attributes update mode.
     * <p>
     * WRITE_BEHIND - session changes stored asynchronously.
     * AFTER_REQUEST - session changes stored only on io.micronaut.session.SessionStore#save(io.micronaut.session.Session) method invocation.
     * <p>
     * Default is AFTER_REQUEST.
     *
     * @param updateMode - mode value
     */
    public void setUpdateMode(UpdateMode updateMode) {
        this.updateMode = updateMode;
    }

    public Codec getCodec() {
        return codec;
    }

    /**
     * Redis data codec applied to session values.
     * Default is MarshallingCodec codec
     *
     * @see org.redisson.client.codec.Codec
     * @see org.redisson.codec.MarshallingCodec
     *
     * @param codec - data codec
     * @return config
     */
    public void setCodec(Codec codec) {
        this.codec = codec;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Defines string prefix applied to all objects stored in Redis.
     *
     * @param keyPrefix - key prefix value
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
