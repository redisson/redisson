// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.SECONDS;
import io.netty.util.concurrent.Future;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.lambdaworks.redis.output.ListScanResult;
import com.lambdaworks.redis.protocol.ConnectionWatchdog;

/**
 * A synchronous thread-safe connection to a redis server. Multiple threads may
 * share one {@link RedisConnection} provided they avoid blocking and transactional
 * operations such as {@link #blpop} and {@link #multi()}/{@link #exec}.
 *
 * A {@link ConnectionWatchdog} monitors each connection and reconnects
 * automatically until {@link #close} is called. All pending commands will be
 * (re)sent after successful reconnection.
 *
 * @author Will Glozer
 */
public class RedisConnection<K, V> {
    protected RedisAsyncConnection<K, V> c;
    protected long timeout;
    protected TimeUnit unit;

    public RedisClient getRedisClient() {
        return c.getRedisClient();
    }

    /**
     * Initialize a new connection.
     *
     * @param c  Underlying async connection.
     */
    public RedisConnection(RedisAsyncConnection<K, V> c) {
        this.c       = c;
        this.timeout = c.timeout;
        this.unit    = c.unit;
    }

    /**
     * Set the command timeout for this connection.
     *
     * @param timeout   Command timeout.
     * @param unit      Unit of time for the timeout.
     */
    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit    = unit;
        c.setTimeout(timeout, unit);
    }

    public Long append(K key, V value) {
        return await(c.append(key, value));
    }

    public String auth(String password) {
        return c.auth(password);
    }

    public String bgrewriteaof() {
        return await(c.bgrewriteaof());
    }

    public String bgsave() {
        return await(c.bgsave());
    }

    public Long bitcount(K key) {
        return await(c.bitcount(key));
    }

    public Long bitcount(K key, long start, long end) {
        return await(c.bitcount(key, start, end));
    }

    public Long bitopAnd(K destination, K... keys) {
        return await(c.bitopAnd(destination, keys));
    }

    public Long bitopNot(K destination, K source) {
        return await(c.bitopNot(destination, source));
    }

    public Long bitopOr(K destination, K... keys) {
        return await(c.bitopOr(destination, keys));
    }
    public Long bitopXor(K destination, K... keys) {
        return await(c.bitopXor(destination, keys));
    }

    public KeyValue<K, V> blpop(long timeout, K... keys) throws InterruptedException {
        long timeout2 = (timeout == 0 ? Long.MAX_VALUE : max(timeout, unit.toSeconds(this.timeout)));
        return awaitInterruptibly(c.blpop(timeout, keys), timeout2, SECONDS);
    }

    public KeyValue<K, V> brpop(long timeout, K... keys) {
        long timeout2 = (timeout == 0 ? Long.MAX_VALUE : max(timeout, unit.toSeconds(this.timeout)));
        return await(c.brpop(timeout, keys), timeout2, SECONDS);
    }

    public V brpoplpush(long timeout, K source, K destination) throws InterruptedException {
        long timeout2 = (timeout == 0 ? Long.MAX_VALUE : max(timeout, unit.toSeconds(this.timeout)));
        return awaitInterruptibly(c.brpoplpush(timeout, source, destination), timeout2, SECONDS);
    }

    public K clientGetname() {
        return await(c.clientGetname());
    }

    public String clientSetname(K name) {
        return await(c.clientSetname(name));
    }

    public String clientKill(String addr) {
        return await(c.clientKill(addr));
    }

    public String clientList() {
        return await(c.clientList());
    }

    public List<String> configGet(String parameter) {
        return await(c.configGet(parameter));
    }

    public String configResetstat() {
        return await(c.configResetstat());
    }

    public String configSet(String parameter, String value) {
        return await(c.configSet(parameter, value));
    }

    public Long dbsize() {
        return await(c.dbsize());
    }

    public String debugObject(K key) {
        return await(c.debugObject(key));
    }

    public Long decr(K key) {
        return await(c.decr(key));
    }

    public Long decrby(K key, long amount) {
        return await(c.decrby(key, amount));
    }

    public Long del(K... keys) {
        return await(c.del(keys));
    }

    public String discard() {
        return await(c.discard());
    }

    public byte[] dump(K key) {
        return await(c.dump(key));
    }

    public V echo(V msg) {
        return await(c.echo(msg));
    }

    /**
     * Eval the supplied script, which must result in the requested
     * {@link ScriptOutputType type}.
     *
     * @param script    Lua script to evaluate.
     * @param type      Script output type.
     * @param keys      Redis keys to pass to script.
     *
     * @param <T>       Expected return type.
     *
     * @return The result of evaluating the script.
     */
    @SuppressWarnings("unchecked")
    public <T> T eval(V script, ScriptOutputType type, K... keys) {
        return (T) await(c.eval(script, type, keys, (V[]) new Object[0]));
    }

    @SuppressWarnings("unchecked")
    public <T> T eval(V script, ScriptOutputType type, K[] keys, V... values) {
        return (T) await(c.eval(script, type, keys, values));
    }

    /**
     * Eval a pre-loaded script identified by its SHA-1 digest, which must result
     * in the requested {@link ScriptOutputType type}.
     *
     * @param digest    Lowercase hex string of script's SHA-1 digest.
     * @param type      Script output type.
     * @param keys      Redis keys to pass to script.
     *
     * @param <T>       Expected return type.
     *
     * @return The result of evaluating the script.
     */
    @SuppressWarnings("unchecked")
    public <T> T evalsha(String digest, ScriptOutputType type, K... keys) {
        return (T) await(c.evalsha(digest, type, keys, (V[]) new Object[0]));
    }

    @SuppressWarnings("unchecked")
    public <T> T evalsha(String digest, ScriptOutputType type, K[] keys, V... values) {
        return (T) await(c.evalsha(digest, type, keys, values));
    }

    public Long pfadd(K key, V... values) {
        return await(c.pfadd(key, values));
    }

    public Long pfcount(K key, K... keys) {
        return await(c.pfcount(key, keys));
    }

    public Long pfmerge(K destkey, K... sourceKeys) {
        return await(c.pfmerge(destkey, sourceKeys));
    }

    public Boolean exists(K key) {
        return await(c.exists(key));
    }

    public Boolean expire(K key, long seconds) {
        return await(c.expire(key, seconds));
    }

    public Boolean expireat(K key, Date timestamp) {
        return await(c.expireat(key, timestamp));
    }

    public Boolean expireat(K key, long timestamp) {
        return await(c.expireat(key, timestamp));
    }

    public List<Object> exec() {
        return await(c.exec());
    }

    public String flushall() {
        return await(c.flushall());
    }

    public String flushdb() {
        return await(c.flushdb());
    }

    public V get(K key) {
        return await(c.get(key));
    }

    public Long getbit(K key, long offset) {
        return await(c.getbit(key, offset));
    }

    public V getrange(K key, long start, long end) {
        return await(c.getrange(key, start, end));
    }

    public V getset(K key, V value) {
        return await(c.getset(key, value));
    }

    public Long hdel(K key, K... fields) {
        return await(c.hdel(key, fields));
    }

    public Boolean hexists(K key, K field) {
        return await(c.hexists(key, field));
    }

    public V hget(K key, K field) {
        return await(c.hget(key, field));
    }

    public Long hincrby(K key, K field, long amount) {
        return await(c.hincrby(key, field, amount));
    }

    public String hincrbyfloat(K key, K field, String amount) {
        return await(c.hincrbyfloat(key, field, amount));
    }

    public Map<K, V> hgetall(K key) {
        return await(c.hgetall(key));
    }

    public Set<K> hkeys(K key) {
        return await(c.hkeys(key));
    }

    public Long hlen(K key) {
        return await(c.hlen(key));
    }

    public List<V> hmget(K key, K... fields) {
        return await(c.hmget(key, fields));
    }

    public String hmset(K key, Map<K, V> map) {
        return await(c.hmset(key, map));
    }

    public Boolean hset(K key, K field, V value) {
        return await(c.hset(key, field, value));
    }

    public Boolean hsetnx(K key, K field, V value) {
        return await(c.hsetnx(key, field, value));
    }

    public List<V> hvals(K key) {
        return await(c.hvals(key));
    }

    public Long incr(K key) {
        return await(c.incr(key));
    }

    public Long incrby(K key, long amount) {
        return await(c.incrby(key, amount));
    }

    public String incrbyfloat(K key, String amount) {
        return await(c.incrbyfloat(key, amount));
    }

    public String info() {
        return await(c.info());
    }

    public String info(String section) {
        return await(c.info(section));
    }

    public List<K> keys(K pattern) {
        return await(c.keys(pattern));
    }

    public Date lastsave() {
        return await(c.lastsave());
    }

    public V lindex(K key, long index) {
        return await(c.lindex(key, index));
    }

    public Long linsert(K key, boolean before, V pivot, V value) {
        return await(c.linsert(key, before, pivot, value));
    }

    public Long llen(K key) {
        return await(c.llen(key));
    }

    public V lpop(K key) {
        return await(c.lpop(key));
    }

    public Long lpush(K key, V... values) {
        return await(c.lpush(key, values));
    }

    public Long lpushx(K key, V value) {
        return await(c.lpushx(key, value));
    }

    public List<V> lrange(K key, long start, long stop) {
        return await(c.lrange(key, start, stop));
    }

    public Long lrem(K key, long count, V value) {
        return await(c.lrem(key, count, value));
    }

    public String lset(K key, long index, V value) {
        return await(c.lset(key, index, value));
    }

    public String ltrim(K key, long start, long stop) {
        return await(c.ltrim(key, start, stop));
    }

    public String migrate(String host, int port, K key, int db, long timeout) {
        return await(c.migrate(host, port, key, db, timeout));
    }

    public List<V> mget(K... keys) {
        return await(c.mget(keys));
    }

    public Boolean move(K key, int db) {
        return await(c.move(key, db));
    }

    public String multi() {
        return await(c.multi());
    }

    public String mset(Map<K, V> map) {
        return await(c.mset(map));
    }

    public Boolean msetnx(Map<K, V> map) {
        return await(c.msetnx(map));
    }

    public String objectEncoding(K key) {
        return await(c.objectEncoding(key));
    }

    public Long objectIdletime(K key) {
        return await(c.objectIdletime(key));
    }

    public Long objectRefcount(K key) {
        return await(c.objectRefcount(key));
    }

    public Boolean persist(K key) {
        return await(c.persist(key));
    }

    public Boolean pexpire(K key, long milliseconds) {
        return await(c.pexpire(key, milliseconds));
    }

    public Boolean pexpireat(K key, Date timestamp) {
        return await(c.pexpireat(key, timestamp));
    }

    public Boolean pexpireat(K key, long timestamp) {
        return await(c.pexpireat(key, timestamp));
    }


    public String ping() {
        return await(c.ping());
    }

    public Long pttl(K key) {
        return await(c.pttl(key));
    }

    public Long publish(K channel, V message) {
        return await(c.publish(channel, message));
    }

    public String quit() {
        return await(c.quit());
    }

    public V randomkey() {
        return await(c.randomkey());
    }

    public String rename(K key, K newKey) {
        return await(c.rename(key, newKey));
    }

    public Boolean renamenx(K key, K newKey) {
        return await(c.renamenx(key, newKey));
    }

    public String restore(K key, long ttl, byte[] value) {
        return await(c.restore(key,  ttl, value));
    }

    public V rpop(K key) {
        return await(c.rpop(key));
    }

    public V rpoplpush(K source, K destination) {
        return await(c.rpoplpush(source, destination));
    }

    public Long rpush(K key, V... values) {
        return await(c.rpush(key, values));
    }

    public Long rpushx(K key, V value) {
        return await(c.rpushx(key, value));
    }

    public Long sadd(K key, V... members) {
        return await(c.sadd(key, members));
    }

    public String save() {
        return await(c.save());
    }

    public Long scard(K key) {
        return await(c.scard(key));
    }

    public List<Boolean> scriptExists(String... digests) {
        return await(c.scriptExists(digests));
    }

    public String scriptFlush() {
        return await(c.scriptFlush());
    }

    public String scriptKill() {
        return await(c.scriptKill());
    }

    public String scriptLoad(V script) {
        return await(c.scriptLoad(script));
    }

    public Set<V> sdiff(K... keys) {
        return await(c.sdiff(keys));
    }

    public Long sdiffstore(K destination, K... keys) {
        return await(c.sdiffstore(destination, keys));
    }

    public String select(int db) {
        return c.select(db);
    }

    public String set(K key, V value) {
        return await(c.set(key, value));
    }

    public Long setbit(K key, long offset, int value) {
        return await(c.setbit(key, offset, value));
    }

    public String setex(K key, long seconds, V value) {
        return await(c.setex(key, seconds, value));
    }

    public String psetex(K key, long millis, V value) {
        return await(c.psetex(key, millis, value));
    }

    public Boolean setnx(K key, V value) {
        return await(c.setnx(key, value));
    }

    public String setexnx(K key, V value, long millis) {
        return await(c.setexnx(key, value, millis));
    }

    public Long setrange(K key, long offset, V value) {
        return await(c.setrange(key, offset, value));
    }

    @Deprecated
    public void shutdown() {
        c.shutdown();
    }

    public void shutdown(boolean save) {
        c.shutdown(save);
    }

    public Set<V> sinter(K... keys) {
        return await(c.sinter(keys));
    }

    public Long sinterstore(K destination, K... keys) {
        return await(c.sinterstore(destination, keys));
    }

    public Boolean sismember(K key, V member) {
        return await(c.sismember(key, member));
    }

    public Boolean smove(K source, K destination, V member) {
        return await(c.smove(source, destination, member));
    }

    public String slaveof(String host, int port) {
        return await(c.slaveof(host, port));
    }

    public String slaveofNoOne() {
        return await(c.slaveofNoOne());
    }

    public List<Object> slowlogGet() {
        return await(c.slowlogGet());
    }

    public List<Object> slowlogGet(int count) {
        return await(c.slowlogGet(count));
    }

    public Long slowlogLen() {
        return await(c.slowlogLen());
    }

    public String slowlogReset() {
        return await(c.slowlogReset());
    }

    public Set<V> smembers(K key) {
        return await(c.smembers(key));
    }

    public List<V> sort(K key) {
        return await(c.sort(key));
    }

    public List<V> sort(K key, SortArgs sortArgs) {
        return await(c.sort(key, sortArgs));
    }

    public Long sortStore(K key, SortArgs sortArgs, K destination) {
        return await(c.sortStore(key, sortArgs, destination));
    }

    public V spop(K key) {
        return await(c.spop(key));
    }

    public V srandmember(K key) {
        return await(c.srandmember(key));
    }

    public Set<V> srandmember(K key, long count) {
        return await(c.srandmember(key, count));
    }

    public Long srem(K key, V... members) {
        return await(c.srem(key, members));
    }

    public Set<V> sunion(K... keys) {
        return await(c.sunion(keys));
    }

    public Long sunionstore(K destination, K... keys) {
        return await(c.sunionstore(destination, keys));
    }

    public String sync() {
        return await(c.sync());
    }

    public Long strlen(K key) {
        return await(c.strlen(key));
    }

    public Long ttl(K key) {
        return await(c.ttl(key));
    }

    public String type(K key) {
        return await(c.type(key));
    }

    public String watch(K... keys) {
        return await(c.watch(keys));
    }

    public String unwatch() {
        return await(c.unwatch());
    }

    public Long zadd(K key, double score, V member) {
        return await(c.zadd(key, score, member));
    }

    public Long zadd(K key, Object... scoresAndValues) {
        return await(c.zadd(key, scoresAndValues));
    }

    public Long zcard(K key) {
        return await(c.zcard(key));
    }

    public Long zcount(K key, double min, double max) {
        return await(c.zcount(key, min, max));
    }

    public Long zcount(K key, String min, String max) {
        return await(c.zcount(key, min, max));
    }

    public Double zincrby(K key, double amount, K member) {
        return await(c.zincrby(key, amount, member));
    }

    public Long zinterstore(K destination, K... keys) {
        return await(c.zinterstore(destination, keys));
    }

    public Long zinterstore(K destination, ZStoreArgs storeArgs, K... keys) {
        return await(c.zinterstore(destination, storeArgs, keys));
    }

    public List<V> zrange(K key, long start, long stop) {
        return await(c.zrange(key, start, stop));
    }

    public List<ScoredValue<V>> zrangeWithScores(K key, long start, long stop) {
        return await(c.zrangeWithScores(key, start, stop));
    }

    public List<V> zrangebyscore(K key, double min, double max) {
        return await(c.zrangebyscore(key, min, max));
    }

    public List<V> zrangebyscore(K key, String min, String max) {
        return await(c.zrangebyscore(key, min, max));
    }

    public List<V> zrangebyscore(K key, double min, double max, long offset, long count) {
        return await(c.zrangebyscore(key, min, max, offset, count));
    }

    public List<V> zrangebyscore(K key, String min, String max, long offset, long count) {
        return await(c.zrangebyscore(key, min, max, offset, count));
    }

    public List<ScoredValue<V>> zrangebyscoreWithScores(K key, double min, double max) {
        return await(c.zrangebyscoreWithScores(key, min, max));
    }

    public List<ScoredValue<V>> zrangebyscoreWithScores(K key, String min, String max) {
        return await(c.zrangebyscoreWithScores(key, min, max));
    }

    public List<ScoredValue<V>> zrangebyscoreWithScores(K key, double min, double max, long offset, long count) {
        return await(c.zrangebyscoreWithScores(key, min, max, offset, count));
    }

    public List<ScoredValue<V>> zrangebyscoreWithScores(K key, String min, String max, long offset, long count) {
        return await(c.zrangebyscoreWithScores(key, min, max, offset, count));
    }

    public Long zrank(K key, V member) {
        return await(c.zrank(key, member));
    }

    public Long zrem(K key, V... members) {
        return await(c.zrem(key, members));
    }

    public Long zremrangebyrank(K key, long start, long stop) {
        return await(c.zremrangebyrank(key, start, stop));
    }

    public Long zremrangebyscore(K key, double min, double max) {
        return await(c.zremrangebyscore(key, min, max));
    }

    public Long zremrangebyscore(K key, String min, String max) {
        return await(c.zremrangebyscore(key, min, max));
    }

    public List<String> time() {
        return await(c.time());
    }

    public List<V> zrevrange(K key, long start, long stop) {
        return await(c.zrevrange(key, start, stop));
    }

    public List<ScoredValue<V>> zrevrangeWithScores(K key, long start, long stop) {
        return await(c.zrevrangeWithScores(key, start, stop));
    }

    public List<V> zrevrangebyscore(K key, double max, double min) {
        return await(c.zrevrangebyscore(key, max, min));
    }

    public List<V> zrevrangebyscore(K key, String max, String min) {
        return await(c.zrevrangebyscore(key, max, min));
    }

    public List<V> zrevrangebyscore(K key, double max, double min, long offset, long count) {
        return await(c.zrevrangebyscore(key, max, min, offset, count));
    }

    public List<V> zrevrangebyscore(K key, String max, String min, long offset, long count) {
        return await(c.zrevrangebyscore(key, max, min, offset, count));
    }

    public List<ScoredValue<V>> zrevrangebyscoreWithScores(K key, double max, double min) {
        return await(c.zrevrangebyscoreWithScores(key, max, min));
    }

    public List<ScoredValue<V>> zrevrangebyscoreWithScores(K key, String max, String min) {
        return await(c.zrevrangebyscoreWithScores(key, max, min));
    }

    public List<ScoredValue<V>> zrevrangebyscoreWithScores(K key, double max, double min, long offset, long count) {
        return await(c.zrevrangebyscoreWithScores(key, max, min, offset, count));
    }

    public List<ScoredValue<V>> zrevrangebyscoreWithScores(K key, String max, String min, long offset, long count) {
        return await(c.zrevrangebyscoreWithScores(key, max, min, offset, count));
    }

    public Long zrevrank(K key, V member) {
        return await(c.zrevrank(key, member));
    }

    public Double zscore(K key, V member) {
        return await(c.zscore(key, member));
    }

    public Long zunionstore(K destination, K... keys) {
        return await(c.zunionstore(destination, keys));
    }

    public Long zunionstore(K destination, ZStoreArgs storeArgs, K... keys) {
        return await(c.zunionstore(destination, storeArgs, keys));
    }

    public ListScanResult<V> sscan(K key, long startValue) {
        return await(c.sscan(key, startValue));
    }

    public ListScanResult<V> zscan(K key, long startValue) {
        return await(c.zscan(key, startValue));
    }

    public RedisAsyncConnection<K, V> getAsync() {
        return c;
    }

    /**
     * Close the connection.
     */
    public void close() {
        c.close();
    }

    /**
     * Generate SHA-1 digest for the supplied script.
     *
     * @param script    Lua script.
     *
     * @return Script digest as a lowercase hex string.
     */
    public String digest(V script) {
        return c.digest(script);
    }

    private <T> T await(Future<T> future, long timeout, TimeUnit unit) {
        return c.await(future, timeout, unit);
    }
    
    private <T> T awaitInterruptibly(Future<T> future, long timeout, TimeUnit unit) throws InterruptedException {
        return c.awaitInterruptibly(future, timeout, unit);
    }


    private <T> T await(Future<T> future) {
        return c.await(future, timeout, unit);
    }
}
