package org.redisson;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

public class RedisRunner {

    public enum OPTIONS {

        BINARY_PATH,
        DAEMONIZE,
        PIDFILE,
        PORT,
        TCP_BACKLOG,
        BIND(true),
        UNIXSOCKET,
        UNIXSOCKETPERM,
        TIMEOUT,
        TCP_KEEPALIVE,
        LOGLEVEL,
        LOGFILE,
        SYSLOG_ENABLED,
        SYSLOG_IDENT,
        SYSLOG_FACILITY,
        DATABASES,
        SAVE(true),
        STOP_WRITES_ON_BGSAVE_ERROR,
        RDBCOMPRESSION,
        RDBCHECKSUM,
        DBFILENAME,
        DIR,
        SLAVEOF,
        MASTERAUTH,
        SLAVE_SERVE_STALE_DATA,
        SLAVE_READ_ONLY,
        REPL_DISKLESS_SYNC,
        REPL_DISKLESS_SYNC_DELAY,
        REPL_PING_SLAVE_PERIOD,
        REPL_TIMEOUT,
        REPL_DISABLE_TCP_NODELAY,
        REPL_BACKLOG_SIZE,
        REPL_BACKLOG_TTL,
        SLAVE_PRIORITY,
        MIN_SLAVES_TO_WRITE,
        MIN_SLAVES_MAX_LAG,
        REQUREPASS,
        RENAME_COMMAND(true),
        MAXCLIENTS,
        MAXMEMORY,
        MAXMEMORY_POLICY,
        MAXMEMORY_SAMPLE,
        APPEND_ONLY,
        APPENDFILENAME,
        APPENDFSYNC,
        NO_APPENDFSYNC_ON_REWRITE,
        AUTO_AOF_REWRITE_PERCENTAGE,
        AUTO_AOF_REWRITE_MIN_SIZE,
        AOF_LOAD_TRUNCATED,
        LUA_TIME_LIMIT,
        CLUSTER_ENABLED,
        CLUSTER_CONFIG_FILE,
        CLUSTER_NODE_TIMEOUT,
        CLUSTER_SLAVE_VALIDITY_FACTOR,
        CLUSTER_MIGRATION_BARRIER,
        CLUSTER_REQUIRE_FULL_COVERAGE,
        SLOWLOG_LOG_SLOWER_THAN,
        SLOWLOG_MAX_LEN,
        LATENCY_MONITOR_THRESHOLD,
        NOFITY_KEYSPACE_EVENTS,
        HASH_MAX_ZIPLIST_ENTRIES,
        HASH_MAX_ZIPLIST_VALUE,
        LIST_MAX_ZIPLIST_ENTRIES,
        LIST_MAX_ZIPLIST_VALUE,
        SET_MAX_INTSET_ENTRIES,
        ZSET_MAX_ZIPLIST_ENTRIES,
        ZSET_MAX_ZIPLIST_VALUE,
        HLL_SPARSE_MAX_BYTES,
        ACTIVEREHASHING,
        CLIENT_OUTPUT_BUFFER_LIMIT$NORMAL,
        CLIENT_OUTPUT_BUFFER_LIMIT$SLAVE,
        CLIENT_OUTPUT_BUFFER_LIMIT$PUBSUB,
        HZ,
        AOF_REWRITE_INCREMENTAL_FSYNC;

        private final boolean allowMutiple;

        private OPTIONS() {
            this.allowMutiple = false;
        }

        private OPTIONS(boolean allowMutiple) {
            this.allowMutiple = allowMutiple;
        }

        public boolean isAllowMultiple() {
            return allowMutiple;
        }
    }

    public enum LOGLEVEL_OPTIONS {

        DEBUG,
        VERBOSE,
        NOTICE,
        WARNING
    }

    public enum SYSLOG_FACILITY_OPTIONS {

        USER,
        LOCAL0,
        LOCAL1,
        LOCAL2,
        LOCAL3,
        LOCAL4,
        LOCAL5,
        LOCAL6,
        LOCAL7
    }

    public enum MAX_MEMORY_POLICY_OPTIONS {

        VOLATILE_LRU,
        ALLKEYS_LRU,
        VOLATILE_RANDOM,
        ALLKEYS_RANDOM,
        VOLATILE_TTL,
        NOEVICTION
    }

    public enum APPEND_FSYNC_MODE_OPTIONS {

        ALWAYS,
        EVERYSEC,
        NO
    }

    public enum KEYSPACE_EVENTS_OPTIONS {

        K,
        E,
        g,
        $,
        l,
        s,
        h,
        z,
        x,
        e,
        A
    }

    private static final String redisFolder = "C:\\Devel\\projects\\redis\\Redis-x64-3.0.500\\";
    private static final String redisBinary = redisFolder + "redis-server.exe";

    private final LinkedHashMap<OPTIONS, String> options = new LinkedHashMap<>();

    {
        options.put(OPTIONS.BINARY_PATH,
                Optional.ofNullable(System.getProperty("redisBinary"))
                .orElse(redisBinary));
    }

    /**
     * To change the <b>redisBinary</b> system property for running the test,
     * use <i>argLine</i> option from surefire plugin:
     *
     * $ mvn -DargLine="-DredisBinary=`which redis-server`" -Punit-test clean \
     * verify
     *
     * @param configPath
     * @return Process running redis instance
     * @throws IOException
     * @throws InterruptedException
     * @see
     * <a href="http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#argLine">
     * http://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#argLine</a>
     */
    public static Process runRedisWithConfigFile(String configPath) throws IOException, InterruptedException {
        URL resource = RedisRunner.class.getResource(configPath);

        ProcessBuilder master = new ProcessBuilder(
                Optional.ofNullable(System.getProperty("redisBinary"))
                .orElse(redisBinary),
                resource.getFile().substring(1));
        master.directory(new File(redisFolder));
        Process p = master.start();
        Thread.sleep(1000);
        return p;
    }

    private void addConfigOption(OPTIONS option, String... args) {
        StringBuilder sb = new StringBuilder(" --")
                .append(option.toString()
                        .replaceAll("_", "-")
                        .replaceAll("\\$", " ")
                        .toLowerCase())
                .append(" ")
                .append(Arrays.stream(args)
                        .collect(Collectors.joining(" ")));
        this.options.put(option, 
                option.isAllowMultiple()
                        ? sb.insert(0, this.options.getOrDefault(option, "")).toString()
                        : sb.toString());
    }

    private String convertBoolean(boolean b) {
        return b ? "yes" : "no";
    }

    public RedisRunner daemonize(boolean daemonize) {
        addConfigOption(OPTIONS.DAEMONIZE, convertBoolean(daemonize));
        return this;
    }

    public RedisRunner pidfile(String pidfile) {
        addConfigOption(OPTIONS.PIDFILE, pidfile);
        return this;
    }

    public RedisRunner port(int port) {
        addConfigOption(OPTIONS.PORT, port + "");
        return this;
    }

    public RedisRunner tcpBacklog(int tcpBacklog) {
        addConfigOption(OPTIONS.TCP_BACKLOG, "" + tcpBacklog);
        return this;
    }

    public RedisRunner bind(String bind) {
        addConfigOption(OPTIONS.BIND, bind);
        return this;
    }

    public RedisRunner timeout(long timeout) {
        addConfigOption(OPTIONS.TIMEOUT, "" + timeout);
        return this;
    }

    public RedisRunner tcpKeepalive(long tcpKeepalive) {
        addConfigOption(OPTIONS.TCP_KEEPALIVE, "" + tcpKeepalive);
        return this;
    }
    
    public RedisRunner loglevel(LOGLEVEL_OPTIONS loglevel) {
        addConfigOption(OPTIONS.LOGLEVEL, loglevel.toString());
        return this;
    }
    
    public RedisRunner logfile(String logfile) {
        addConfigOption(OPTIONS.LOGLEVEL, logfile);
        return this;
    }

    public Process run() throws IOException, InterruptedException {
        ProcessBuilder master = new ProcessBuilder(
                options.values().stream()
                .collect(Collectors.joining()));
        master.directory(new File(redisBinary).getParentFile());
        Process p = master.start();
        Thread.sleep(1000);
        return p;
    }

}
