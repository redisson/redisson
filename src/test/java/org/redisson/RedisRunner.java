package org.redisson;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;

public class RedisRunner {

    public enum REDIS_OPTIONS {

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
        APPENDONLY,
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

        private REDIS_OPTIONS() {
            this.allowMutiple = false;
        }

        private REDIS_OPTIONS(boolean allowMutiple) {
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

    private final LinkedHashMap<REDIS_OPTIONS, String> options = new LinkedHashMap<>();
    private static RedisRunner.RedisProcess defaultRedisInstance;
    private static int defaultRedisInstanceExitCode;

    private String defaultDir = Paths.get("").toString();
    private boolean nosave = false;
    private boolean randomDir = false;
    private ArrayList<String> bindAddr = new ArrayList<>();
    private int port = 6379;

    {
        this.options.put(REDIS_OPTIONS.BINARY_PATH, RedissonRuntimeEnvironment.redisBinaryPath);
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
    public static RedisProcess runRedisWithConfigFile(String configPath) throws IOException, InterruptedException {
        URL resource = RedisRunner.class.getResource(configPath);
        return runWithOptions(new RedisRunner(), RedissonRuntimeEnvironment.redisBinaryPath, resource.getFile());
    }

    private static RedisProcess runWithOptions(RedisRunner runner, String... options) throws IOException, InterruptedException {
        List<String> launchOptions = Arrays.stream(options)
                .map(x -> Arrays.asList(x.split(" "))).flatMap(x -> x.stream())
                .collect(Collectors.toList());
        System.out.println("REDIS LAUNCH OPTIONS: " + Arrays.toString(launchOptions.toArray()));
        ProcessBuilder master = new ProcessBuilder(launchOptions)
                .redirectErrorStream(true)
                .directory(new File(RedissonRuntimeEnvironment.tempDir));
        Process p = master.start();
        new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            try {
                while (p.isAlive() && (line = reader.readLine()) != null && !RedissonRuntimeEnvironment.isTravis) {
                    System.out.println("REDIS PROCESS: " + line);
                }
            } catch (IOException ex) {
                System.out.println("Exception: " + ex.getLocalizedMessage());
            }
        }).start();
        Thread.sleep(1500);
        return new RedisProcess(p, runner);
    }

    public RedisProcess run() throws IOException, InterruptedException {
        if (!options.containsKey(REDIS_OPTIONS.DIR)) {
            options.put(REDIS_OPTIONS.DIR, defaultDir);
        }
        return runWithOptions(this, options.values().toArray(new String[0]));
    }

    private void addConfigOption(REDIS_OPTIONS option, Object... args) {
        StringBuilder sb = new StringBuilder("--")
                .append(option.toString()
                        .replaceAll("_", "-")
                        .replaceAll("\\$", " ")
                        .toLowerCase())
                .append(" ")
                .append(Arrays.stream(args).map(Object::toString)
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
        addConfigOption(REDIS_OPTIONS.DAEMONIZE, convertBoolean(daemonize));
        return this;
    }

    public RedisRunner pidfile(String pidfile) {
        addConfigOption(REDIS_OPTIONS.PIDFILE, pidfile);
        return this;
    }

    public RedisRunner port(int port) {
        this.port = port;
        addConfigOption(REDIS_OPTIONS.PORT, port);
        return this;
    }

    public int getPort() {
        return this.port;
    }

    public RedisRunner tcpBacklog(long tcpBacklog) {
        addConfigOption(REDIS_OPTIONS.TCP_BACKLOG, tcpBacklog);
        return this;
    }

    public RedisRunner bind(String bind) {
        this.bindAddr.add(bind);
        addConfigOption(REDIS_OPTIONS.BIND, bind);
        return this;
    }

    public ArrayList<String> getBindAddr() {
        return this.bindAddr;
    }

    public RedisRunner unixsocket(String unixsocket) {
        addConfigOption(REDIS_OPTIONS.UNIXSOCKET, unixsocket);
        return this;
    }

    public RedisRunner unixsocketperm(int unixsocketperm) {
        addConfigOption(REDIS_OPTIONS.UNIXSOCKETPERM, unixsocketperm);
        return this;
    }

    public RedisRunner timeout(long timeout) {
        addConfigOption(REDIS_OPTIONS.TIMEOUT, timeout);
        return this;
    }

    public RedisRunner tcpKeepalive(long tcpKeepalive) {
        addConfigOption(REDIS_OPTIONS.TCP_KEEPALIVE, tcpKeepalive);
        return this;
    }

    public RedisRunner loglevel(LOGLEVEL_OPTIONS loglevel) {
        addConfigOption(REDIS_OPTIONS.LOGLEVEL, loglevel.toString());
        return this;
    }

    public RedisRunner logfile(String logfile) {
        addConfigOption(REDIS_OPTIONS.LOGLEVEL, logfile);
        return this;
    }

    public RedisRunner syslogEnabled(boolean syslogEnabled) {
        addConfigOption(REDIS_OPTIONS.SYSLOG_ENABLED, convertBoolean(syslogEnabled));
        return this;
    }

    public RedisRunner syslogIdent(String syslogIdent) {
        addConfigOption(REDIS_OPTIONS.SYSLOG_IDENT, syslogIdent);
        return this;
    }

    public RedisRunner syslogFacility(SYSLOG_FACILITY_OPTIONS syslogFacility) {
        addConfigOption(REDIS_OPTIONS.SYSLOG_IDENT, syslogFacility.toString());
        return this;
    }

    public RedisRunner databases(int databases) {
        addConfigOption(REDIS_OPTIONS.DATABASES, databases);
        return this;
    }

    public RedisRunner save(long seconds, long changes) {
        if (!nosave) {
            addConfigOption(REDIS_OPTIONS.SAVE, seconds, changes);
        }
        return this;
    }

    /**
     * Phantom option
     *
     * @return RedisRunner
     */
    public RedisRunner nosave() {
        this.nosave = true;
        options.remove(REDIS_OPTIONS.SAVE);
        addConfigOption(REDIS_OPTIONS.SAVE, "''");
        return this;
    }

    public RedisRunner stopWritesOnBgsaveError(boolean stopWritesOnBgsaveError) {
        addConfigOption(REDIS_OPTIONS.STOP_WRITES_ON_BGSAVE_ERROR, convertBoolean(stopWritesOnBgsaveError));
        return this;
    }

    public RedisRunner rdbcompression(boolean rdbcompression) {
        addConfigOption(REDIS_OPTIONS.RDBCOMPRESSION, convertBoolean(rdbcompression));
        return this;
    }

    public RedisRunner rdbchecksum(boolean rdbchecksum) {
        addConfigOption(REDIS_OPTIONS.RDBCHECKSUM, convertBoolean(rdbchecksum));
        return this;
    }

    public RedisRunner dbfilename(String dbfilename) {
        addConfigOption(REDIS_OPTIONS.DBFILENAME, dbfilename);
        return this;
    }

    public RedisRunner dir(String dir) {
        if (!randomDir) {
            addConfigOption(REDIS_OPTIONS.DIR, dir);
        }
        return this;
    }

    /**
     * Phantom option
     *
     * @return RedisRunner
     */
    public RedisRunner randomDir() {
        this.randomDir = true;
        options.remove(REDIS_OPTIONS.DIR);
        makeRandomDefaultDir();
        addConfigOption(REDIS_OPTIONS.DIR, defaultDir);
        return this;
    }

    public RedisRunner slaveof(Inet4Address masterip, int port) {
        addConfigOption(REDIS_OPTIONS.SLAVEOF, masterip.getHostAddress(), port);
        return this;
    }

    public RedisRunner masterauth(String masterauth) {
        addConfigOption(REDIS_OPTIONS.MASTERAUTH, masterauth);
        return this;
    }

    public RedisRunner slaveServeStaleData(boolean slaveServeStaleData) {
        addConfigOption(REDIS_OPTIONS.SLAVE_SERVE_STALE_DATA, convertBoolean(slaveServeStaleData));
        return this;
    }

    public RedisRunner slaveReadOnly(boolean slaveReadOnly) {
        addConfigOption(REDIS_OPTIONS.SLAVE_READ_ONLY, convertBoolean(slaveReadOnly));
        return this;
    }

    public RedisRunner replDisklessSync(boolean replDisklessSync) {
        addConfigOption(REDIS_OPTIONS.REPL_DISKLESS_SYNC, convertBoolean(replDisklessSync));
        return this;
    }

    public RedisRunner replDisklessSyncDelay(long replDisklessSyncDelay) {
        addConfigOption(REDIS_OPTIONS.REPL_DISKLESS_SYNC_DELAY, replDisklessSyncDelay);
        return this;
    }

    public RedisRunner replPingSlavePeriod(long replPingSlavePeriod) {
        addConfigOption(REDIS_OPTIONS.REPL_PING_SLAVE_PERIOD, replPingSlavePeriod);
        return this;
    }

    public RedisRunner replTimeout(long replTimeout) {
        addConfigOption(REDIS_OPTIONS.REPL_TIMEOUT, replTimeout);
        return this;
    }

    public RedisRunner replDisableTcpNodelay(boolean replDisableTcpNodelay) {
        addConfigOption(REDIS_OPTIONS.REPL_DISABLE_TCP_NODELAY, convertBoolean(replDisableTcpNodelay));
        return this;
    }

    public RedisRunner replBacklogSize(String replBacklogSize) {
        addConfigOption(REDIS_OPTIONS.REPL_BACKLOG_SIZE, replBacklogSize);
        return this;
    }

    public RedisRunner replBacklogTtl(long replBacklogTtl) {
        addConfigOption(REDIS_OPTIONS.REPL_BACKLOG_TTL, replBacklogTtl);
        return this;
    }

    public RedisRunner slavePriority(long slavePriority) {
        addConfigOption(REDIS_OPTIONS.SLAVE_PRIORITY, slavePriority);
        return this;
    }

    public RedisRunner minSlaveToWrite(long minSlaveToWrite) {
        addConfigOption(REDIS_OPTIONS.MIN_SLAVES_TO_WRITE, minSlaveToWrite);
        return this;
    }

    public RedisRunner minSlaveMaxLag(long minSlaveMaxLag) {
        addConfigOption(REDIS_OPTIONS.MIN_SLAVES_MAX_LAG, minSlaveMaxLag);
        return this;
    }

    public RedisRunner requirepass(String requirepass) {
        addConfigOption(REDIS_OPTIONS.REQUREPASS, requirepass);
        return this;
    }

    public RedisRunner renameCommand(String renameCommand) {
        addConfigOption(REDIS_OPTIONS.RENAME_COMMAND, renameCommand);
        return this;
    }

    public RedisRunner maxclients(long maxclients) {
        addConfigOption(REDIS_OPTIONS.MAXCLIENTS, maxclients);
        return this;
    }

    public RedisRunner maxmemory(String maxmemory) {
        addConfigOption(REDIS_OPTIONS.MAXMEMORY, maxmemory);
        return this;
    }

    public RedisRunner maxmemoryPolicy(MAX_MEMORY_POLICY_OPTIONS maxmemoryPolicy) {
        addConfigOption(REDIS_OPTIONS.MAXMEMORY, maxmemoryPolicy.toString());
        return this;
    }

    public RedisRunner maxmemorySamples(long maxmemorySamples) {
        addConfigOption(REDIS_OPTIONS.MAXMEMORY, maxmemorySamples);
        return this;
    }

    public RedisRunner appendonly(boolean appendonly) {
        addConfigOption(REDIS_OPTIONS.APPENDONLY, convertBoolean(appendonly));
        return this;
    }

    public RedisRunner appendfilename(String appendfilename) {
        addConfigOption(REDIS_OPTIONS.APPENDFILENAME, appendfilename);
        return this;
    }

    public RedisRunner appendfsync(APPEND_FSYNC_MODE_OPTIONS appendfsync) {
        addConfigOption(REDIS_OPTIONS.APPENDFSYNC, appendfsync.toString());
        return this;
    }

    public RedisRunner noAppendfsyncOnRewrite(boolean noAppendfsyncOnRewrite) {
        addConfigOption(REDIS_OPTIONS.NO_APPENDFSYNC_ON_REWRITE, convertBoolean(noAppendfsyncOnRewrite));
        return this;
    }

    public RedisRunner autoAofRewritePercentage(int autoAofRewritePercentage) {
        addConfigOption(REDIS_OPTIONS.AUTO_AOF_REWRITE_PERCENTAGE, autoAofRewritePercentage);
        return this;
    }

    public RedisRunner autoAofRewriteMinSize(String autoAofRewriteMinSize) {
        addConfigOption(REDIS_OPTIONS.AUTO_AOF_REWRITE_MIN_SIZE, autoAofRewriteMinSize);
        return this;
    }

    public RedisRunner aofLoadTruncated(boolean aofLoadTruncated) {
        addConfigOption(REDIS_OPTIONS.AOF_LOAD_TRUNCATED, convertBoolean(aofLoadTruncated));
        return this;
    }

    public RedisRunner luaTimeLimit(long luaTimeLimit) {
        addConfigOption(REDIS_OPTIONS.AOF_LOAD_TRUNCATED, luaTimeLimit);
        return this;
    }

    public RedisRunner clusterEnabled(boolean clusterEnabled) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_ENABLED, convertBoolean(clusterEnabled));
        return this;
    }

    public RedisRunner clusterConfigFile(String clusterConfigFile) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_CONFIG_FILE, clusterConfigFile);
        return this;
    }

    public RedisRunner clusterNodeTimeout(long clusterNodeTimeout) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_NODE_TIMEOUT, clusterNodeTimeout);
        return this;
    }

    public RedisRunner clusterSlaveValidityFactor(long clusterSlaveValidityFactor) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_SLAVE_VALIDITY_FACTOR, clusterSlaveValidityFactor);
        return this;
    }

    public RedisRunner clusterMigrationBarrier(long clusterMigrationBarrier) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_MIGRATION_BARRIER, clusterMigrationBarrier);
        return this;
    }

    public RedisRunner clusterRequireFullCoverage(boolean clusterRequireFullCoverage) {
        addConfigOption(REDIS_OPTIONS.CLUSTER_REQUIRE_FULL_COVERAGE, convertBoolean(clusterRequireFullCoverage));
        return this;
    }

    public RedisRunner slowlogLogSlowerThan(long slowlogLogSlowerThan) {
        addConfigOption(REDIS_OPTIONS.SLOWLOG_LOG_SLOWER_THAN, slowlogLogSlowerThan);
        return this;
    }

    public RedisRunner slowlogMaxLen(long slowlogMaxLen) {
        addConfigOption(REDIS_OPTIONS.SLOWLOG_MAX_LEN, slowlogMaxLen);
        return this;
    }

    public RedisRunner latencyMonitorThreshold(long latencyMonitorThreshold) {
        addConfigOption(REDIS_OPTIONS.LATENCY_MONITOR_THRESHOLD, latencyMonitorThreshold);
        return this;
    }

    public RedisRunner notifyKeyspaceEvents(KEYSPACE_EVENTS_OPTIONS notifyKeyspaceEvents) {
        String existing = this.options.getOrDefault(REDIS_OPTIONS.CLUSTER_CONFIG_FILE, "");
        addConfigOption(REDIS_OPTIONS.CLUSTER_CONFIG_FILE,
                existing.contains(notifyKeyspaceEvents.toString())
                ? existing
                : (existing + notifyKeyspaceEvents.toString()));
        return this;
    }

    public RedisRunner hashMaxZiplistEntries(long hashMaxZiplistEntries) {
        addConfigOption(REDIS_OPTIONS.HASH_MAX_ZIPLIST_ENTRIES, hashMaxZiplistEntries);
        return this;
    }

    public RedisRunner hashMaxZiplistValue(long hashMaxZiplistValue) {
        addConfigOption(REDIS_OPTIONS.HASH_MAX_ZIPLIST_VALUE, hashMaxZiplistValue);
        return this;
    }

    public RedisRunner listMaxZiplistEntries(long listMaxZiplistEntries) {
        addConfigOption(REDIS_OPTIONS.LIST_MAX_ZIPLIST_ENTRIES, listMaxZiplistEntries);
        return this;
    }

    public RedisRunner listMaxZiplistValue(long listMaxZiplistValue) {
        addConfigOption(REDIS_OPTIONS.LIST_MAX_ZIPLIST_VALUE, listMaxZiplistValue);
        return this;
    }

    public RedisRunner setMaxIntsetEntries(long setMaxIntsetEntries) {
        addConfigOption(REDIS_OPTIONS.SET_MAX_INTSET_ENTRIES, setMaxIntsetEntries);
        return this;
    }

    public RedisRunner zsetMaxZiplistEntries(long zsetMaxZiplistEntries) {
        addConfigOption(REDIS_OPTIONS.ZSET_MAX_ZIPLIST_ENTRIES, zsetMaxZiplistEntries);
        return this;
    }

    public RedisRunner zsetMaxZiplistValue(long zsetMaxZiplistValue) {
        addConfigOption(REDIS_OPTIONS.ZSET_MAX_ZIPLIST_VALUE, zsetMaxZiplistValue);
        return this;
    }

    public RedisRunner hllSparseMaxBytes(long hllSparseMaxBytes) {
        addConfigOption(REDIS_OPTIONS.HLL_SPARSE_MAX_BYTES, hllSparseMaxBytes);
        return this;
    }

    public RedisRunner activerehashing(boolean activerehashing) {
        addConfigOption(REDIS_OPTIONS.ACTIVEREHASHING, convertBoolean(activerehashing));
        return this;
    }

    public RedisRunner clientOutputBufferLimit$Normal(String hardLimit, String softLimit, long softSeconds) {
        addConfigOption(REDIS_OPTIONS.CLIENT_OUTPUT_BUFFER_LIMIT$NORMAL, hardLimit, softLimit, softSeconds);
        return this;
    }

    public RedisRunner clientOutputBufferLimit$Slave(String hardLimit, String softLimit, long softSeconds) {
        addConfigOption(REDIS_OPTIONS.CLIENT_OUTPUT_BUFFER_LIMIT$SLAVE, hardLimit, softLimit, softSeconds);
        return this;
    }

    public RedisRunner clientOutputBufferLimit$Pubsub(String hardLimit, String softLimit, long softSeconds) {
        addConfigOption(REDIS_OPTIONS.CLIENT_OUTPUT_BUFFER_LIMIT$PUBSUB, hardLimit, softLimit, softSeconds);
        return this;
    }

    public RedisRunner hz(int hz) {
        addConfigOption(REDIS_OPTIONS.HZ, hz);
        return this;
    }

    public RedisRunner aofRewriteIncrementalFsync(boolean aofRewriteIncrementalFsync) {
        addConfigOption(REDIS_OPTIONS.AOF_REWRITE_INCREMENTAL_FSYNC, convertBoolean(aofRewriteIncrementalFsync));
        return this;
    }

    public boolean isRandomDir() {
        return this.randomDir;
    }

    public boolean isNosave() {
        return this.nosave;
    }

    public String defaultDir() {
        return this.defaultDir;
    }

    public String getInitialBindAddr() {
        return bindAddr.size() > 0 ? bindAddr.get(0) : "localhost";
    }

    public boolean deleteDBfileDir() {
        File f = new File(defaultDir);
        if (f.exists()) {
            System.out.println("REDIS RUNNER: Deleting directory " + defaultDir);
            return f.delete();
        }
        return false;
    }

    private void makeRandomDefaultDir() {
        File f = new File(RedissonRuntimeEnvironment.tempDir + "/" + UUID.randomUUID());
        if (f.exists()) {
            makeRandomDefaultDir();
        } else {
            System.out.println("REDIS RUNNER: Making directory " + f.getAbsolutePath());
            f.mkdirs();
            this.defaultDir = f.getAbsolutePath();
        }
    }

    public static final class RedisProcess {

        private final Process redisProcess;
        private final RedisRunner runner;
        private RedisVersion redisVersion;
        
        private RedisProcess(Process redisProcess, RedisRunner runner) {
            this.redisProcess = redisProcess;
            this.runner = runner;
        }

        public int stop() throws InterruptedException {
            if (runner.isNosave() && !runner.isRandomDir()) {
                RedisClient c = createDefaultRedisClientInstance();
                RedisConnection connection = c.connect();
                connection.async(new RedisStrictCommand<Void>("SHUTDOWN", "NOSAVE", new VoidReplayConvertor()))
                        .await(3, TimeUnit.SECONDS);
                c.shutdown();
                connection.closeAsync().syncUninterruptibly();
            }
            redisProcess.destroy();
            int exitCode = redisProcess.isAlive() ? redisProcess.waitFor() : redisProcess.exitValue();
            if (runner.isRandomDir()) {
                runner.deleteDBfileDir();
            }
            return exitCode == 1 && RedissonRuntimeEnvironment.isWindows ? 0 : exitCode;
        }

        public Process getRedisProcess() {
            return redisProcess;
        }

        public RedisClient createRedisClientInstance() {
            if (redisProcess.isAlive()) {
                return new RedisClient(runner.getInitialBindAddr(), runner.getPort());
            }
            throw new IllegalStateException("Redis server instance is not running.");
        }

        public RedisVersion getRedisVersion() {
            if (redisVersion == null) {
                redisVersion = new RedisVersion(createRedisClientInstance().serverInfo().get("redis_version"));
            }
            return redisVersion;
        }

    }

    public static RedisRunner.RedisProcess startDefaultRedisServerInstance() throws IOException, InterruptedException {
        if (defaultRedisInstance == null) {
            System.out.println("REDIS RUNNER: Starting up default instance...");
            defaultRedisInstance = new RedisRunner().nosave().randomDir().run();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    shutDownDefaultRedisServerInstance();
                } catch (InterruptedException ex) {
                }
            }));
        }
        return defaultRedisInstance;
    }

    public static int shutDownDefaultRedisServerInstance() throws InterruptedException {
        if (defaultRedisInstance != null) {
            System.out.println("REDIS RUNNER: Shutting down default instance...");
            try {
                defaultRedisInstanceExitCode = defaultRedisInstance.stop();
            } finally {
                defaultRedisInstance = null;
            }
        } else {
            System.out.println("REDIS RUNNER: Default instance is already down with an exit code " + defaultRedisInstanceExitCode);
        }
        return defaultRedisInstanceExitCode;
    }

    public static boolean isDefaultRedisServerInstanceRunning() {
        return defaultRedisInstance != null && defaultRedisInstance.redisProcess.isAlive();
    }

    public static RedisClient createDefaultRedisClientInstance() {
        return defaultRedisInstance.createRedisClientInstance();
    }

    public static RedisRunner.RedisProcess getDefaultRedisServerInstance() {
        return defaultRedisInstance;
    }
}
