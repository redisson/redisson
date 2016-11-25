/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.connection.ConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class QueueTransferTask {
    
    private static final Logger log = LoggerFactory.getLogger(QueueTransferTask.class);

    private int usage = 1;
    private final AtomicReference<Timeout> timeoutReference = new AtomicReference<Timeout>();
    private final ConnectionManager connectionManager;
    
    public QueueTransferTask(ConnectionManager connectionManager) {
        super();
        this.connectionManager = connectionManager;
    }

    public void incUsage() {
        usage++;
    }
    
    public int decUsage() {
        usage--;
        return usage;
    }
    
    private int messageListenerId;
    private int statusListenerId;
    
    public void start() {
        RTopic<Long> schedulerTopic = getTopic();
        statusListenerId = schedulerTopic.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                pushTask();
            }
        });
        
        messageListenerId = schedulerTopic.addListener(new MessageListener<Long>() {
            @Override
            public void onMessage(String channel, Long startTime) {
                scheduleTask(startTime);
            }
        });
    }
    
    public void stop() {
        RTopic<Long> schedulerTopic = getTopic();
        schedulerTopic.removeListener(messageListenerId);
        schedulerTopic.removeListener(statusListenerId);
    }

    private void scheduleTask(final Long startTime) {
        if (startTime == null) {
            return;
        }
        
        Timeout oldTimeout = timeoutReference.get();
        if (oldTimeout != null) {
            oldTimeout.cancel();
            timeoutReference.compareAndSet(oldTimeout, null);
        }
        
        long delay = startTime - System.currentTimeMillis();
        if (delay > 10) {
            Timeout timeout = connectionManager.newTimeout(new TimerTask() {                    
                @Override
                public void run(Timeout timeout) throws Exception {
                    pushTask();
                }
            }, delay, TimeUnit.MILLISECONDS);
            timeoutReference.set(timeout);
        } else {
            pushTask();
        }
    }
    
    protected abstract RTopic<Long> getTopic();
    
    protected abstract RFuture<Long> pushTaskAsync();
    
    private void pushTask() {
        RFuture<Long> startTimeFuture = pushTaskAsync();
        addListener(startTimeFuture);
    }

    private void addListener(RFuture<Long> startTimeFuture) {
        startTimeFuture.addListener(new FutureListener<Long>() {
            @Override
            public void operationComplete(io.netty.util.concurrent.Future<Long> future) throws Exception {
                if (!future.isSuccess()) {
                    if (future.cause() instanceof RedissonShutdownException) {
                        return;
                    }
                    log.error(future.cause().getMessage(), future.cause());
                    scheduleTask(System.currentTimeMillis() + 5 * 1000L);
                    return;
                }
                
                if (future.getNow() != null) {
                    scheduleTask(future.getNow());
                }
            }
        });
    }

    
}
