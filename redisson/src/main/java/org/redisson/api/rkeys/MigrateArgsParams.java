package org.redisson.api.rkeys;

import org.redisson.api.MigrateMode;

/**
 * Arguments objects for RKeys.migrate()
 *
 * @author lyrric
 */
public class MigrateArgsParams implements OptionalMigrateArgs{

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

    public MigrateArgsParams(String[] keys) {
        this.keys = keys;
    }

    @Override
    public OptionalMigrateArgs host(String host) {
        this.host = host;
        return this;
    }

    @Override
    public OptionalMigrateArgs port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public OptionalMigrateArgs database(int database) {
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
