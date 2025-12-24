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
package org.redisson.api.keys;

import org.redisson.api.MigrateMode;

/**
 * Arguments objects for RKeys.migrate()
 *
 * @author lyrric
 */
public class MigrateParams implements MigrateArgs, HostMigrateArgs, PortMigrateArgs, DatabaseMigrateArgs, TimeoutMigrateArgs, OptionalMigrateArgs {
    /**
     * keys to transfer Redis version >= 3.0.6
     */
    private final String[] keys;
    /**
     * destination host
     */
    private String host;
    /**
     * destination port
     */
    private int port;
    /**
     * destination database
     */
    private int database;
    /**
     * maximum idle time in any moment of the communication with the destination instance in milliseconds
     */
    private long timeout;
    /**
     * migration mode
     */
    private MigrateMode mode = MigrateMode.MIGRATE;
    /**
     * destination username Redis version >= 6.0.0
     */
    private String username;
    /**
     * destination password Redis version >= 4.0.7
     */
    private String password;

    public MigrateParams(String[] keys) {
        this.keys = keys;
    }

    @Override
    public PortMigrateArgs host(String host) {
        this.host = host;
        return this;
    }

    @Override
    public DatabaseMigrateArgs port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public TimeoutMigrateArgs database(int database) {
        this.database = database;
        return this;
    }

    @Override
    public OptionalMigrateArgs timeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public OptionalMigrateArgs mode(MigrateMode mode) {
        this.mode = mode;
        return this;
    }

    @Override
    public OptionalMigrateArgs username(String username) {
        this.username = username;
        return this;
    }

    @Override
    public OptionalMigrateArgs password(String password) {
        this.password = password;
        return this;
    }

    public String[] getKeys() {
        return keys;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getDatabase() {
        return database;
    }

    public long getTimeout() {
        return timeout;
    }

    public MigrateMode getMode() {
        return mode;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
