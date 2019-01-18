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
package org.redisson.client.protocol;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.channel.ChannelPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class QueueCommandHolder {

    final AtomicBoolean sent = new AtomicBoolean();
    final ChannelPromise channelPromise;
    final QueueCommand command;

    public QueueCommandHolder(QueueCommand command, ChannelPromise channelPromise) {
        super();
        this.command = command;
        this.channelPromise = channelPromise;
    }

    public QueueCommand getCommand() {
        return command;
    }

    public ChannelPromise getChannelPromise() {
        return channelPromise;
    }

    public boolean trySend() {
        return sent.compareAndSet(false, true);
    }

    @Override
    public String toString() {
        return "QueueCommandHolder [command=" + command + "]";
    }

}
