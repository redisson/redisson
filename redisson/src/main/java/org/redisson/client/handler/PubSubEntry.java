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
package org.redisson.client.handler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.pubsub.Message;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PubSubEntry {

    private final MultiDecoder<Object> decoder;
    
    private final Queue<Message> queue = new ConcurrentLinkedQueue<Message>();

    private final AtomicBoolean sent = new AtomicBoolean();
    
    public PubSubEntry(MultiDecoder<Object> decoder) {
        super();
        this.decoder = decoder;
    }
    
    public MultiDecoder<Object> getDecoder() {
        return decoder;
    }
    
    public Queue<Message> getQueue() {
        return queue;
    }
    
    public AtomicBoolean getSent() {
        return sent;
    }
    
}
