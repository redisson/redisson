/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.core.RKeys;
import org.redisson.misc.CompositeIterable;

import io.netty.util.concurrent.Future;

public class RedissonKeys implements RKeys {

    private final CommandExecutor commandExecutor;

    public RedissonKeys(CommandExecutor commandExecutor) {
        super();
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Iterable<String> getKeysByPattern(final String pattern) {
        List<Iterable<String>> iterables = new ArrayList<Iterable<String>>();
        for (final Integer slot : commandExecutor.getConnectionManager().getEntries().keySet()) {
            Iterable<String> iterable = new Iterable<String>() {
                @Override
                public Iterator<String> iterator() {
                    return createKeysIterator(slot, pattern);
                }
            };
            iterables.add(iterable);
        }
        return new CompositeIterable<String>(iterables);
    }

    @Override
    public Iterable<String> getKeys() {
        List<Iterable<String>> iterables = new ArrayList<Iterable<String>>();
        for (final Integer slot : commandExecutor.getConnectionManager().getEntries().keySet()) {
            Iterable<String> iterable = new Iterable<String>() {
                @Override
                public Iterator<String> iterator() {
                    return createKeysIterator(slot, null);
                }
            };
            iterables.add(iterable);
        }
        return new CompositeIterable<String>(iterables);
    }

    private ListScanResult<String> scanIterator(int slot, long startPos, String pattern) {
        if (pattern == null) {
            return commandExecutor.write(slot, StringCodec.INSTANCE, RedisCommands.SCAN, startPos);
        }
        return commandExecutor.write(slot, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "MATCH", pattern);
    }

    private Iterator<String> createKeysIterator(final int slot, final String pattern) {
        return new Iterator<String>() {

            private Iterator<String> iter;
            private Long iterPos;

            private boolean removeExecuted;
            private String value;

            @Override
            public boolean hasNext() {
                if (iter == null) {
                    ListScanResult<String> res = scanIterator(slot, 0, pattern);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                } else if (!iter.hasNext() && iterPos != 0) {
                    ListScanResult<String> res = scanIterator(slot, iterPos, pattern);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                }
                return iter.hasNext();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element");
                }

                value = iter.next();
                removeExecuted = false;
                return value;
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }

                iter.remove();
                delete(value);
                removeExecuted = true;
            }

        };
    }

    @Override
    public String randomKey() {
        return commandExecutor.get(randomKeyAsync());
    }

    @Override
    public Future<String> randomKeyAsync() {
        return commandExecutor.readRandomAsync(RedisCommands.RANDOM_KEY);
    }

    /**
     * Find keys by key search pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    @Override
    public Collection<String> findKeysByPattern(String pattern) {
        return commandExecutor.get(findKeysByPatternAsync(pattern));
    }

    /**
     * Find keys by key search pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    @Override
    public Future<Collection<String>> findKeysByPatternAsync(String pattern) {
        return commandExecutor.readAllAsync(RedisCommands.KEYS, pattern);
    }

    /**
     * Delete multiple objects by a key pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    @Override
    public long deleteByPattern(String pattern) {
        return commandExecutor.get(deleteByPatternAsync(pattern));
    }

    /**
     * Delete multiple objects by a key pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    @Override
    public Future<Long> deleteByPatternAsync(String pattern) {
        return commandExecutor.evalWriteAllAsync(RedisCommands.EVAL_INTEGER, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, "local keys = redis.call('keys', ARGV[1]) "
                + "local n = 0 "
                + "for i=1, table.getn(keys),5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end "
            + "return n;",Collections.emptyList(), pattern);
    }

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    @Override
    public long delete(String ... keys) {
        return commandExecutor.get(deleteAsync(keys));
    }

    /**
     * Delete multiple objects by name in async mode
     *
     * @param keys - object names
     * @return
     */
    @Override
    public Future<Long> deleteAsync(String ... keys) {
        return commandExecutor.writeAllAsync(RedisCommands.DEL, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, (Object[])keys);
    }


}
