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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Single;
import org.redisson.RedissonBinaryStream;
import org.redisson.api.RBinaryStream;
import org.redisson.api.RFuture;

import java.nio.ByteBuffer;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBinaryStreamRx {

    private final CommandRxExecutor commandExecutor;
    private final RedissonBinaryStream.RedissonAsynchronousByteChannel channel;

    public RedissonBinaryStreamRx(CommandRxExecutor commandExecutor, RBinaryStream stream) {
        this.commandExecutor = commandExecutor;
        channel = (RedissonBinaryStream.RedissonAsynchronousByteChannel) stream.getAsynchronousChannel();
    }

    public long position() {
        return channel.position();
    }

    public void position(long newPosition) {
        channel.position(newPosition);
    }

    public Single<Integer> read(ByteBuffer buf) {
        return commandExecutor.flowable(() -> ((RFuture<Integer>) channel.read(buf))).singleOrError();
    }

    public Single<Integer> write(ByteBuffer buf) {
        return commandExecutor.flowable(() -> ((RFuture<Integer>) channel.write(buf))).singleOrError();
    }

}
