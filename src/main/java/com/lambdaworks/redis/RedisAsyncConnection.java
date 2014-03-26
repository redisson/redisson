// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis;

import static com.lambdaworks.redis.protocol.CommandKeyword.AFTER;
import static com.lambdaworks.redis.protocol.CommandKeyword.AND;
import static com.lambdaworks.redis.protocol.CommandKeyword.BEFORE;
import static com.lambdaworks.redis.protocol.CommandKeyword.ENCODING;
import static com.lambdaworks.redis.protocol.CommandKeyword.FLUSH;
import static com.lambdaworks.redis.protocol.CommandKeyword.GETNAME;
import static com.lambdaworks.redis.protocol.CommandKeyword.IDLETIME;
import static com.lambdaworks.redis.protocol.CommandKeyword.KILL;
import static com.lambdaworks.redis.protocol.CommandKeyword.LEN;
import static com.lambdaworks.redis.protocol.CommandKeyword.LIMIT;
import static com.lambdaworks.redis.protocol.CommandKeyword.LIST;
import static com.lambdaworks.redis.protocol.CommandKeyword.LOAD;
import static com.lambdaworks.redis.protocol.CommandKeyword.NO;
import static com.lambdaworks.redis.protocol.CommandKeyword.NOSAVE;
import static com.lambdaworks.redis.protocol.CommandKeyword.NOT;
import static com.lambdaworks.redis.protocol.CommandKeyword.ONE;
import static com.lambdaworks.redis.protocol.CommandKeyword.OR;
import static com.lambdaworks.redis.protocol.CommandKeyword.REFCOUNT;
import static com.lambdaworks.redis.protocol.CommandKeyword.RESET;
import static com.lambdaworks.redis.protocol.CommandKeyword.RESETSTAT;
import static com.lambdaworks.redis.protocol.CommandKeyword.SETNAME;
import static com.lambdaworks.redis.protocol.CommandKeyword.WITHSCORES;
import static com.lambdaworks.redis.protocol.CommandKeyword.XOR;
import static com.lambdaworks.redis.protocol.CommandType.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.lambdaworks.codec.Base16;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.output.BooleanListOutput;
import com.lambdaworks.redis.output.BooleanOutput;
import com.lambdaworks.redis.output.ByteArrayOutput;
import com.lambdaworks.redis.output.DateOutput;
import com.lambdaworks.redis.output.DoubleOutput;
import com.lambdaworks.redis.output.IntegerOutput;
import com.lambdaworks.redis.output.KeyListOutput;
import com.lambdaworks.redis.output.KeyOutput;
import com.lambdaworks.redis.output.KeyValueOutput;
import com.lambdaworks.redis.output.MapKeyListOutput;
import com.lambdaworks.redis.output.MapOutput;
import com.lambdaworks.redis.output.MapValueListOutput;
import com.lambdaworks.redis.output.MapValueOutput;
import com.lambdaworks.redis.output.MultiOutput;
import com.lambdaworks.redis.output.NestedMultiOutput;
import com.lambdaworks.redis.output.ScoredValueListOutput;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.output.StringListOutput;
import com.lambdaworks.redis.output.ValueListOutput;
import com.lambdaworks.redis.output.ValueOutput;
import com.lambdaworks.redis.output.ValueSetOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandArgs;
import com.lambdaworks.redis.protocol.CommandOutput;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.ConnectionWatchdog;

/**
 * An asynchronous thread-safe connection to a redis server. Multiple threads may
 * share one {@link RedisAsyncConnection} provided they avoid blocking and transactional
 * operations such as {@link #blpop} and {@link #multi()}/{@link #exec}.
 *
 * A {@link ConnectionWatchdog} monitors each connection and reconnects
 * automatically until {@link #close} is called. All pending commands will be
 * (re)sent after successful reconnection.
 *
 * @author Will Glozer
 */
@ChannelHandler.Sharable
public class RedisAsyncConnection<K, V> extends ChannelInboundHandlerAdapter {
    protected BlockingQueue<Command<K, V, ?>> queue;
    protected RedisCodec<K, V> codec;
    protected Channel channel;
    protected long timeout;
    protected TimeUnit unit;
    protected MultiOutput<K, V> multi;
    private String password;
    private int db;
    private boolean closed;

    /**
     * Initialize a new connection.
     *
     * @param queue   Command queue.
     * @param codec   Codec used to encode/decode keys and values.
     * @param timeout Maximum time to wait for a response.
     * @param unit    Unit of time for the timeout.
     */
    public RedisAsyncConnection(BlockingQueue<Command<K, V, ?>> queue, RedisCodec<K, V> codec, long timeout, TimeUnit unit) {
        this.queue = queue;
        this.codec = codec;
        this.timeout = timeout;
        this.unit = unit;
    }

    /**
     * Set the command timeout for this connection.
     *
     * @param timeout Command timeout.
     * @param unit    Unit of time for the timeout.
     */
    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
    }

    public Future<Long> append(K key, V value) {
        return dispatch(APPEND, new IntegerOutput<K, V>(codec), key, value);
    }

    public String auth(String password) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(password);
        Command<K, V, String> cmd = dispatch(AUTH, new StatusOutput<K, V>(codec), args);
        String status = await(cmd, timeout, unit);
        if ("OK".equals(status)) this.password = password;
        return status;
    }

    public Future<String> bgrewriteaof() {
        return dispatch(BGREWRITEAOF, new StatusOutput<K, V>(codec));
    }

    public Future<String> bgsave() {
        return dispatch(BGSAVE, new StatusOutput<K, V>(codec));
    }

    public Future<Long> bitcount(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        return dispatch(BITCOUNT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> bitcount(K key, long start, long end) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(start).add(end);
        return dispatch(BITCOUNT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> bitopAnd(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(AND).addKey(destination).addKeys(keys);
        return dispatch(BITOP, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> bitopNot(K destination, K source) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(NOT).addKey(destination).addKey(source);
        return dispatch(BITOP, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> bitopOr(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(OR).addKey(destination).addKeys(keys);
        return dispatch(BITOP, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> bitopXor(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(XOR).addKey(destination).addKeys(keys);
        return dispatch(BITOP, new IntegerOutput<K, V>(codec), args);
    }

    public Future<KeyValue<K, V>> blpop(long timeout, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys).add(timeout);
        return dispatch(BLPOP, new KeyValueOutput<K, V>(codec), args);
    }

    public Future<KeyValue<K, V>> brpop(long timeout, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys).add(timeout);
        return dispatch(BRPOP, new KeyValueOutput<K, V>(codec), args);
    }

    public Future<V> brpoplpush(long timeout, K source, K destination) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(source).addKey(destination).add(timeout);
        return dispatch(BRPOPLPUSH, new ValueOutput<K, V>(codec), args);
    }

    public Future<K> clientGetname() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(GETNAME);
        return dispatch(CLIENT, new KeyOutput<K, V>(codec), args);
    }

    public Future<String> clientSetname(K name) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(SETNAME).addKey(name);
        return dispatch(CLIENT, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> clientKill(String addr) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(KILL).add(addr);
        return dispatch(CLIENT, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> clientList() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(LIST);
        return dispatch(CLIENT, new StatusOutput<K, V>(codec), args);
    }

    public Future<List<String>> configGet(String parameter) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(GET).add(parameter);
        return dispatch(CONFIG, new StringListOutput<K, V>(codec), args);
    }

    public Future<String> configResetstat() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(RESETSTAT);
        return dispatch(CONFIG, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> configSet(String parameter, String value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(SET).add(parameter).add(value);
        return dispatch(CONFIG, new StatusOutput<K, V>(codec), args);
    }

    public Future<Long> dbsize() {
        return dispatch(DBSIZE, new IntegerOutput<K, V>(codec));
    }

    public Future<String> debugObject(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(OBJECT).addKey(key);
        return dispatch(DEBUG, new StatusOutput<K, V>(codec), args);
    }

    public Future<Long> decr(K key) {
        return dispatch(DECR, new IntegerOutput<K, V>(codec), key);
    }

    public Future<Long> decrby(K key, long amount) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(amount);
        return dispatch(DECRBY, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> del(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(DEL, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> discard() {
        if (multi != null) {
            multi.cancel();
            multi = null;
        }
        return dispatch(DISCARD, new StatusOutput<K, V>(codec));
    }

    public Future<byte[]> dump(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        return dispatch(DUMP, new ByteArrayOutput<K, V>(codec), args);
    }

    public Future<V> echo(V msg) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addValue(msg);
        return dispatch(ECHO, new ValueOutput<K, V>(codec), args);
    }

    public <T> Future<T> eval(V script, ScriptOutputType type, K[] keys, V... values) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addValue(script).add(keys.length).addKeys(keys).addValues(values);
        CommandOutput<K, V, T> output = newScriptOutput(codec, type);
        return dispatch(EVAL, output, args);
    }

    public <T> Future<T> evalsha(String digest, ScriptOutputType type, K[] keys, V... values) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(digest).add(keys.length).addKeys(keys).addValues(values);
        CommandOutput<K, V, T> output = newScriptOutput(codec, type);
        return dispatch(EVALSHA, output, args);
    }

    public Future<Boolean> exists(K key) {
        return dispatch(EXISTS, new BooleanOutput<K, V>(codec), key);
    }

    public Future<Boolean> expire(K key, long seconds) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(seconds);
        return dispatch(EXPIRE, new BooleanOutput<K, V>(codec), args);
    }

    public Future<Boolean> expireat(K key, Date timestamp) {
        return expireat(key, timestamp.getTime() / 1000);
    }

    public Future<Boolean> expireat(K key, long timestamp) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(timestamp);
        return dispatch(EXPIREAT, new BooleanOutput<K, V>(codec), args);
    }

    public Future<List<Object>> exec() {
        MultiOutput<K, V> multi = this.multi;
        this.multi = null;
        if (multi == null) multi = new MultiOutput<K, V>(codec);
        return dispatch(EXEC, multi);
    }

    public Future<String> flushall() {
        return dispatch(FLUSHALL, new StatusOutput<K, V>(codec));
    }

    public Future<String> flushdb() {
        return dispatch(FLUSHDB, new StatusOutput<K, V>(codec));
    }

    public Future<V> get(K key) {
        return dispatch(GET, new ValueOutput<K, V>(codec), key);
    }

    public Future<Long> getbit(K key, long offset) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(offset);
        return dispatch(GETBIT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<V> getrange(K key, long start, long end) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(end);
        return dispatch(GETRANGE, new ValueOutput<K, V>(codec), args);
    }

    public Future<V> getset(K key, V value) {
        return dispatch(GETSET, new ValueOutput<K, V>(codec), key, value);
    }

    public Future<Long> hdel(K key, K... fields) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapKeys(fields);
        return dispatch(HDEL, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Boolean> hexists(K key, K field) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapKey(field);
        return dispatch(HEXISTS, new BooleanOutput<K, V>(codec), args);
    }

    public Future<V> hget(K key, K field) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapKey(field);
        return dispatch(HGET, new MapValueOutput<K, V>(codec), args);
    }

    public Future<Long> hincrby(K key, K field, long amount) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addKey(field).add(amount);
        return dispatch(HINCRBY, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Double> hincrbyfloat(K key, K field, double amount) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addKey(field).add(amount);
        return dispatch(HINCRBYFLOAT, new DoubleOutput<K, V>(codec), args);
    }

    public Future<Map<K, V>> hgetall(K key) {
        return dispatch(HGETALL, new MapOutput<K, V>(codec), key);
    }

    public Future<Set<K>> hkeys(K key) {
        return dispatch(HKEYS, new MapKeyListOutput<K, V>(codec), key);
    }

    public Future<Long> hlen(K key) {
        return dispatch(HLEN, new IntegerOutput<K, V>(codec), key);
    }

    public Future<List<V>> hmget(K key, K... fields) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addKeys(fields);
        return dispatch(HMGET, new ValueListOutput<K, V>(codec), args);
    }

    public Future<String> hmset(K key, Map<K, V> map) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(map);
        return dispatch(HMSET, new StatusOutput<K, V>(codec), args);
    }

    public Future<Boolean> hset(K key, K field, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapKey(field).addMapValue(value);
        return dispatch(HSET, new BooleanOutput<K, V>(codec), args);
    }

    public Future<Boolean> hsetnx(K key, K field, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapKey(field).addMapValue(value);
        return dispatch(HSETNX, new BooleanOutput<K, V>(codec), args);
    }

    public Future<List<V>> hvals(K key) {
        return dispatch(HVALS, new MapValueListOutput<K, V>(codec), key);
    }

    public Future<Long> incr(K key) {
        return dispatch(INCR, new IntegerOutput<K, V>(codec), key);
    }

    public Future<Long> incrby(K key, long amount) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(amount);
        return dispatch(INCRBY, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Double> incrbyfloat(K key, double amount) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(amount);
        return dispatch(INCRBYFLOAT, new DoubleOutput<K, V>(codec), args);
    }

    public Future<String> info() {
        return dispatch(INFO, new StatusOutput<K, V>(codec));
    }

    public Future<String> info(String section) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(section);
        return dispatch(INFO, new StatusOutput<K, V>(codec), args);
    }

    public Future<List<K>> keys(K pattern) {
       return dispatch(KEYS, new KeyListOutput<K, V>(codec), pattern);
    }

    public Future<Date> lastsave() {
        return dispatch(LASTSAVE, new DateOutput<K, V>(codec));
    }

    public Future<V> lindex(K key, long index) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(index);
        return dispatch(LINDEX, new MapValueOutput<K, V>(codec), args);
    }

    public Future<Long> linsert(K key, boolean before, V pivot, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(before ? BEFORE : AFTER).addValue(pivot).addValue(value);
        return dispatch(LINSERT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> llen(K key) {
        return dispatch(LLEN, new IntegerOutput<K, V>(codec), key);
    }

    public Future<V> lpop(K key) {
        return dispatch(LPOP, new MapValueOutput<K, V>(codec), key);
    }

    public Future<Long> lpush(K key, V... values) {
        return dispatch(LPUSH, new IntegerOutput<K, V>(codec), key, values);
    }

    public Future<Long> lpushx(K key, V value) {
        return dispatch(LPUSHX, new IntegerOutput<K, V>(codec), key, value);
    }

    public Future<List<V>> lrange(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(stop);
        return dispatch(LRANGE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<Long> lrem(K key, long count, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(count).addMapValue(value);
        return dispatch(LREM, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> lset(K key, long index, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(index).addValue(value);
        return dispatch(LSET, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> ltrim(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(stop);
        return dispatch(LTRIM, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> migrate(String host, int port, K key, int db, long timeout) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.add(host).add(port).addKey(key).add(db).add(timeout);
        return dispatch(MIGRATE, new StatusOutput<K, V>(codec), args);
    }

    public Future<List<V>> mget(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(MGET, new ValueListOutput<K, V>(codec), args);
    }

    public Future<Boolean> move(K key, int db) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(db);
        return dispatch(MOVE, new BooleanOutput<K, V>(codec), args);
    }

    public Future<String> multi() {
        Command<K, V, String> cmd = dispatch(MULTI, new StatusOutput<K, V>(codec));
        multi = (multi == null ? new MultiOutput<K, V>(codec) : multi);
        return cmd;
    }

    public Future<String> mset(Map<K, V> map) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(map);
        return dispatch(MSET, new StatusOutput<K, V>(codec), args);
    }

    public Future<Boolean> msetnx(Map<K, V> map) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(map);
        return dispatch(MSETNX, new BooleanOutput<K, V>(codec), args);
    }

    public Future<String> objectEncoding(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(ENCODING).addKey(key);
        return dispatch(OBJECT, new StatusOutput<K, V>(codec), args);
    }

    public Future<Long> objectIdletime(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(IDLETIME).addKey(key);
        return dispatch(OBJECT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> objectRefcount(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(REFCOUNT).addKey(key);
        return dispatch(OBJECT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Boolean> persist(K key) {
        return dispatch(PERSIST, new BooleanOutput<K, V>(codec), key);
    }

    public Future<Boolean> pexpire(K key, long milliseconds) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(milliseconds);
        return dispatch(PEXPIRE, new BooleanOutput<K, V>(codec), args);
    }

    public Future<Boolean> pexpireat(K key, Date timestamp) {
        return pexpireat(key, timestamp.getTime());
    }

    public Future<Boolean> pexpireat(K key, long timestamp) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(timestamp);
        return dispatch(PEXPIREAT, new BooleanOutput<K, V>(codec), args);
    }

    public Future<String> ping() {
        return dispatch(PING, new StatusOutput<K, V>(codec));
    }

    public Future<Long> pttl(K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        return dispatch(PTTL, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> publish(K channel, V message) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(channel).addValue(message);
        return dispatch(PUBLISH, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> quit() {
        return dispatch(QUIT, new StatusOutput<K, V>(codec));
    }

    public Future<V> randomkey() {
        return dispatch(RANDOMKEY, new ValueOutput<K, V>(codec));
    }

    public Future<String> rename(K key, K newKey) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addKey(newKey);
        return dispatch(RENAME, new StatusOutput<K, V>(codec), args);
    }

    public Future<Boolean> renamenx(K key, K newKey) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addKey(newKey);
        return dispatch(RENAMENX, new BooleanOutput<K, V>(codec), args);
    }

    public Future<String> restore(K key, long ttl, byte[] value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(ttl).add(value);
        return dispatch(RESTORE, new StatusOutput<K, V>(codec), args);
    }

    public Future<V> rpop(K key) {
        return dispatch(RPOP, new ValueOutput<K, V>(codec), key);
    }

    public Future<V> rpoplpush(K source, K destination) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(source).addKey(destination);
        return dispatch(RPOPLPUSH, new ValueOutput<K, V>(codec), args);
    }

    public Future<Long> rpush(K key, V... values) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapValues(values);
        return dispatch(RPUSH, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> rpushx(K key, V value) {
        return dispatch(RPUSHX, new IntegerOutput<K, V>(codec), key, value);
    }

    public Future<Long> sadd(K key, V... members) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapValues(members);
        return dispatch(SADD, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> save() {
        return dispatch(SAVE, new StatusOutput<K, V>(codec));
    }

    public Future<Long> scard(K key) {
        return dispatch(SCARD, new IntegerOutput<K, V>(codec), key);
    }

    public Future<List<Boolean>> scriptExists(String... digests) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(EXISTS);
        for (String sha : digests) args.add(sha);
        return dispatch(SCRIPT, new BooleanListOutput<K, V>(codec), args);
    }

    public Future<String> scriptFlush() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(FLUSH);
        return dispatch(SCRIPT, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> scriptKill() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(KILL);
        return dispatch(SCRIPT, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> scriptLoad(V script) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(LOAD).addValue(script);
        return dispatch(SCRIPT, new StatusOutput<K, V>(codec), args);
    }

    public Future<Set<V>> sdiff(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(SDIFF, new ValueSetOutput<K, V>(codec), args);
    }

    public Future<Long> sdiffstore(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(destination).addKeys(keys);
        return dispatch(SDIFFSTORE, new IntegerOutput<K, V>(codec), args);
    }

    public String select(int db) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(db);
        Command<K, V, String> cmd = dispatch(SELECT, new StatusOutput<K, V>(codec), args);
        String status = await(cmd, timeout, unit);
        if ("OK".equals(status)) this.db = db;
        return status;
    }

    public Future<String> set(K key, V value) {
        return dispatch(SET, new StatusOutput<K, V>(codec), key, value);
    }

    public Future<Long> setbit(K key, long offset, int value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(offset).add(value);
        return dispatch(SETBIT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> setex(K key, long seconds, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(seconds).addValue(value);
        return dispatch(SETEX, new StatusOutput<K, V>(codec), args);
    }

    public Future<Boolean> setnx(K key, V value) {
        return dispatch(SETNX, new BooleanOutput<K, V>(codec), key, value);
    }

    public Future<Long> setrange(K key, long offset, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(offset).addValue(value);
        return dispatch(SETRANGE, new IntegerOutput<K, V>(codec), args);
    }

    @Deprecated
    public void shutdown() {
        dispatch(SHUTDOWN, new StatusOutput<K, V>(codec));
    }

    public void shutdown(boolean save) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        dispatch(SHUTDOWN, new StatusOutput<K, V>(codec), save ? args.add(SAVE) : args.add(NOSAVE));
    }

    public Future<Set<V>> sinter(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(SINTER, new ValueSetOutput<K, V>(codec), args);
    }

    public Future<Long> sinterstore(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(destination).addKeys(keys);
        return dispatch(SINTERSTORE, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Boolean> sismember(K key, V member) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapValue(member);
        return dispatch(SISMEMBER, new BooleanOutput<K, V>(codec), args);
    }

    public Future<Boolean> smove(K source, K destination, V member) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(source).addKey(destination).addValue(member);
        return dispatch(SMOVE, new BooleanOutput<K, V>(codec), args);
    }

    public Future<String> slaveof(String host, int port) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(host).add(port);
        return dispatch(SLAVEOF, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> slaveofNoOne() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(NO).add(ONE);
        return dispatch(SLAVEOF, new StatusOutput<K, V>(codec), args);
    }

    public Future<List<Object>> slowlogGet() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(GET);
        return dispatch(SLOWLOG, new NestedMultiOutput<K, V>(codec), args);
    }

    public Future<List<Object>> slowlogGet(int count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(GET).add(count);
        return dispatch(SLOWLOG, new NestedMultiOutput<K, V>(codec), args);
    }

    public Future<Long> slowlogLen() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(LEN);
        return dispatch(SLOWLOG, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> slowlogReset() {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(RESET);
        return dispatch(SLOWLOG, new StatusOutput<K, V>(codec), args);
    }

    public Future<Set<V>> smembers(K key) {
        return dispatch(SMEMBERS, new ValueSetOutput<K, V>(codec), key);
    }

    public Future<List<V>> sort(K key) {
        return dispatch(SORT, new ValueListOutput<K, V>(codec), key);
    }

    public Future<List<V>> sort(K key, SortArgs sortArgs) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        sortArgs.build(args, null);
        return dispatch(SORT, new ValueListOutput<K, V>(codec), args);
    }

    public Future<Long> sortStore(K key, SortArgs sortArgs, K destination) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        sortArgs.build(args, destination);
        return dispatch(SORT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<V> spop(K key) {
        return dispatch(SPOP, new ValueOutput<K, V>(codec), key);
    }

    public Future<V> srandmember(K key) {
        return dispatch(SRANDMEMBER, new ValueOutput<K, V>(codec), key);
    }

    public Future<Set<V>> srandmember(K key, long count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(count);
        return dispatch(SRANDMEMBER, new ValueSetOutput<K, V>(codec), args);
    }

    public Future<Long> srem(K key, V... members) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addMapValues(members);
        return dispatch(SREM, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Set<V>> sunion(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(SUNION, new ValueSetOutput<K, V>(codec), args);
    }

    public Future<Long> sunionstore(K destination, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(destination).addKeys(keys);
        return dispatch(SUNIONSTORE, new IntegerOutput<K, V>(codec), args);
    }

    public Future<String> sync() {
        return dispatch(SYNC, new StatusOutput<K, V>(codec));
    }

    public Future<Long> strlen(K key) {
        return dispatch(STRLEN, new IntegerOutput<K, V>(codec), key);
    }

    public Future<Long> ttl(K key) {
        return dispatch(TTL, new IntegerOutput<K, V>(codec), key);
    }

    public Future<String> type(K key) {
        return dispatch(TYPE, new StatusOutput<K, V>(codec), key);
    }

    public Future<String> watch(K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKeys(keys);
        return dispatch(WATCH, new StatusOutput<K, V>(codec), args);
    }

    public Future<String> unwatch() {
        return dispatch(UNWATCH, new StatusOutput<K, V>(codec));
    }

    public Future<Long> zadd(K key, double score, V member) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(score).addValue(member);
        return dispatch(ZADD, new IntegerOutput<K, V>(codec), args);
    }

    @SuppressWarnings("unchecked")
    public Future<Long> zadd(K key, Object... scoresAndValues) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        for (int i = 0; i < scoresAndValues.length; i += 2) {
            args.add((Double) scoresAndValues[i]);
            args.addValue((V) scoresAndValues[i + 1]);
        }
        return dispatch(ZADD, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> zcard(K key) {
        return dispatch(ZCARD, new IntegerOutput<K, V>(codec), key);
    }

    public Future<Long> zcount(K key, double min, double max) {
        return zcount(key, string(min), string(max));
    }

    public Future<Long> zcount(K key, String min, String max) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(min).add(max);
        return dispatch(ZCOUNT, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Double> zincrby(K key, double amount, K member) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(amount).addKey(member);
        return dispatch(ZINCRBY, new DoubleOutput<K, V>(codec), args);
    }

    public Future<Long> zinterstore(K destination, K... keys) {
        return zinterstore(destination, new ZStoreArgs(), keys);
    }

    public Future<Long> zinterstore(K destination, ZStoreArgs storeArgs, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(destination).add(keys.length).addKeys(keys);
        storeArgs.build(args);
        return dispatch(ZINTERSTORE, new IntegerOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrange(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(stop);
        return dispatch(ZRANGE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrangeWithScores(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(start).add(stop).add(WITHSCORES);
        return dispatch(ZRANGE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrangebyscore(K key, double min, double max) {
        return zrangebyscore(key, string(min), string(max));
    }

    public Future<List<V>> zrangebyscore(K key, String min, String max) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(min).add(max);
        return dispatch(ZRANGEBYSCORE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrangebyscore(K key, double min, double max, long offset, long count) {
        return zrangebyscore(key, string(min), string(max), offset, count);
    }

    public Future<List<V>> zrangebyscore(K key, String min, String max, long offset, long count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(min).add(max).add(LIMIT).add(offset).add(count);
        return dispatch(ZRANGEBYSCORE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrangebyscoreWithScores(K key, double min, double max) {
        return zrangebyscoreWithScores(key, string(min), string(max));
    }

    public Future<List<ScoredValue<V>>> zrangebyscoreWithScores(K key, String min, String max) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(min).add(max).add(WITHSCORES);
        return dispatch(ZRANGEBYSCORE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrangebyscoreWithScores(K key, double min, double max, long offset, long count) {
        return zrangebyscoreWithScores(key, string(min), string(max), offset, count);
    }

    public Future<List<ScoredValue<V>>> zrangebyscoreWithScores(K key, String min, String max, long offset, long count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(min).add(max).add(WITHSCORES).add(LIMIT).add(offset).add(count);
        return dispatch(ZRANGEBYSCORE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<Long> zrank(K key, V member) {
        return dispatch(ZRANK, new IntegerOutput<K, V>(codec), key, member);
    }

    public Future<Long> zrem(K key, V... members) {
        return dispatch(ZREM, new IntegerOutput<K, V>(codec), key, members);
    }

    public Future<Long> zremrangebyrank(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(stop);
        return dispatch(ZREMRANGEBYRANK, new IntegerOutput<K, V>(codec), args);
    }

    public Future<Long> zremrangebyscore(K key, double min, double max) {
        return zremrangebyscore(key, string(min), string(max));
    }

    public Future<Long> zremrangebyscore(K key, String min, String max) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(min).add(max);
        return dispatch(ZREMRANGEBYSCORE, new IntegerOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrevrange(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(start).add(stop);
        return dispatch(ZREVRANGE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrevrangeWithScores(K key, long start, long stop) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(start).add(stop).add(WITHSCORES);
        return dispatch(ZREVRANGE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrevrangebyscore(K key, double max, double min) {
        return zrevrangebyscore(key, string(max), string(min));
    }

    public Future<List<V>> zrevrangebyscore(K key, String max, String min) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).add(max).add(min);
        return dispatch(ZREVRANGEBYSCORE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<V>> zrevrangebyscore(K key, double max, double min, long offset, long count) {
        return zrevrangebyscore(key, string(max), string(min), offset, count);
    }

    public Future<List<V>> zrevrangebyscore(K key, String max, String min, long offset, long count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(max).add(min).add(LIMIT).add(offset).add(count);
        return dispatch(ZREVRANGEBYSCORE, new ValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrevrangebyscoreWithScores(K key, double max, double min) {
        return zrevrangebyscoreWithScores(key, string(max), string(min));
    }

    public Future<List<ScoredValue<V>>> zrevrangebyscoreWithScores(K key, String max, String min) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(max).add(min).add(WITHSCORES);
        return dispatch(ZREVRANGEBYSCORE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<List<ScoredValue<V>>> zrevrangebyscoreWithScores(K key, double max, double min, long offset, long count) {
        return zrevrangebyscoreWithScores(key, string(max), string(min), offset, count);
    }

    public Future<List<ScoredValue<V>>> zrevrangebyscoreWithScores(K key, String max, String min, long offset, long count) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(key).add(max).add(min).add(WITHSCORES).add(LIMIT).add(offset).add(count);
        return dispatch(ZREVRANGEBYSCORE, new ScoredValueListOutput<K, V>(codec), args);
    }

    public Future<Long> zrevrank(K key, V member) {
        return dispatch(ZREVRANK, new IntegerOutput<K, V>(codec), key, member);
    }

    public Future<Double> zscore(K key, V member) {
        return dispatch(ZSCORE, new DoubleOutput<K, V>(codec), key, member);
    }

    public Future<Long> zunionstore(K destination, K... keys) {
        return zunionstore(destination, new ZStoreArgs(), keys);
    }

    public Future<Long> zunionstore(K destination, ZStoreArgs storeArgs, K... keys) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec);
        args.addKey(destination).add(keys.length).addKeys(keys);
        storeArgs.build(args);
        return dispatch(ZUNIONSTORE, new IntegerOutput<K, V>(codec), args);
    }

    /**
     * Wait until commands are complete or the connection timeout is reached.
     *
     * @param futures   Futures to wait for.
     *
     * @return True if all futures complete in time.
     */
    public boolean awaitAll(Future<?>... futures) {
        return awaitAll(timeout, unit, futures);
    }

    /**
     * Wait until futures are complete or the supplied timeout is reached.
     *
     * @param timeout   Maximum time to wait for futures to complete.
     * @param unit      Unit of time for the timeout.
     * @param futures   Futures to wait for.
     *
     * @return True if all futures complete in time.
     */
    public boolean awaitAll(long timeout, TimeUnit unit, Future<?>... futures) {
        boolean complete;

        try {
            long nanos = unit.toNanos(timeout);
            long time  = System.nanoTime();

            for (Future<?> f : futures) {
                if (nanos < 0) return false;
                f.get(nanos, TimeUnit.NANOSECONDS);
                long now = System.nanoTime();
                nanos -= now - time;
                time   = now;
            }

            complete = true;
        } catch (TimeoutException e) {
            complete = false;
        } catch (Exception e) {
            throw new RedisCommandInterruptedException(e);
        }

        return complete;
    }

    /**
     * Close the connection.
     */
    public synchronized void close() {
        if (!closed && channel != null) {
            ConnectionWatchdog watchdog = channel.pipeline().get(ConnectionWatchdog.class);
            watchdog.setReconnect(false);
            closed = true;
            channel.close();
        }
    }

    public String digest(V script) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(codec.encodeValue(script));
            return new String(Base16.encode(md.digest(), false));
        } catch (NoSuchAlgorithmException e) {
            throw new RedisException("JVM does not support SHA1");
        }
    }

    @Override
    public synchronized void channelActive(ChannelHandlerContext ctx) throws Exception {
        channel = ctx.channel();

        List<Command<K, V, ?>> tmp = new ArrayList<Command<K, V, ?>>(queue.size() + 2);

        if (password != null) {
            CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(password);
            tmp.add(new Command<K, V, String>(AUTH, new StatusOutput<K, V>(codec), args, false));
        }

        if (db != 0) {
            CommandArgs<K, V> args = new CommandArgs<K, V>(codec).add(db);
            tmp.add(new Command<K, V, String>(SELECT, new StatusOutput<K, V>(codec), args, false));
        }

        tmp.addAll(queue);
        queue.clear();

        for (Command<K, V, ?> cmd : tmp) {
            if (!cmd.isCancelled()) {
                queue.add(cmd);
                channel.writeAndFlush(cmd);
            }
        }

        tmp.clear();
    }

    @Override
    public synchronized void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (closed) {
            for (Command<K, V, ?> cmd : queue) {
                if (cmd.getOutput() != null) {
                    cmd.getOutput().setError("Connection closed");
                }
                cmd.complete();
            }
            queue.clear();
            queue = null;
            channel = null;
        }
    }

    public <T> Command<K, V, T> dispatch(CommandType type, CommandOutput<K, V, T> output) {
        return dispatch(type, output, (CommandArgs<K, V>) null);
    }

    public <T> Command<K, V, T> dispatch(CommandType type, CommandOutput<K, V, T> output, K key) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key);
        return dispatch(type, output, args);
    }

    public <T> Command<K, V, T> dispatch(CommandType type, CommandOutput<K, V, T> output, K key, V value) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addValue(value);
        return dispatch(type, output, args);
    }

    public <T> Command<K, V, T> dispatch(CommandType type, CommandOutput<K, V, T> output, K key, V[] values) {
        CommandArgs<K, V> args = new CommandArgs<K, V>(codec).addKey(key).addValues(values);
        return dispatch(type, output, args);
    }

    public synchronized <T> Command<K, V, T> dispatch(CommandType type, CommandOutput<K, V, T> output, CommandArgs<K, V> args) {
        Command<K, V, T> cmd = new Command<K, V, T>(type, output, args, multi != null);

        try {
            if (multi != null) {
                multi.add(cmd);
            }

            queue.put(cmd);

            if (channel != null) {
                channel.writeAndFlush(cmd);
            }
        } catch (NullPointerException e) {
            throw new RedisException("Connection is closed");
        } catch (InterruptedException e) {
            throw new RedisCommandInterruptedException(e);
        }

        return cmd;
    }

    public <T> T await(Command<K, V, T> cmd, long timeout, TimeUnit unit) {
        if (!cmd.await(timeout, unit)) {
            cmd.cancel(true);
            throw new RedisException("Command timed out");
        }
        CommandOutput<K, V, T> output = cmd.getOutput();
        if (output.hasError()) throw new RedisException(output.getError());
        return output.get();
    }

    @SuppressWarnings("unchecked")
    protected <K, V, T> CommandOutput<K, V, T> newScriptOutput(RedisCodec<K, V> codec, ScriptOutputType type) {
        switch (type) {
            case BOOLEAN: return (CommandOutput<K, V, T>) new BooleanOutput<K, V>(codec);
            case INTEGER: return (CommandOutput<K, V, T>) new IntegerOutput<K, V>(codec);
            case STATUS:  return (CommandOutput<K, V, T>) new StatusOutput<K, V>(codec);
            case MULTI:   return (CommandOutput<K, V, T>) new NestedMultiOutput<K, V>(codec);
            case VALUE:   return (CommandOutput<K, V, T>) new ValueOutput<K, V>(codec);
            default:      throw new RedisException("Unsupported script output type");
        }
    }

    public String string(double n) {
        if (Double.isInfinite(n)) {
            return (n > 0) ? "+inf" : "-inf";
        }
        return Double.toString(n);
    }
}
