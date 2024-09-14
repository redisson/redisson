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
package org.redisson;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.BaseStatusListener;
import org.redisson.api.listener.MessageListener;
import org.redisson.connection.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class QueueTransferTask {
    
    private static final Logger log = LoggerFactory.getLogger(QueueTransferTask.class);

    public static class TimeoutTask {
        
        private final long startTime;
        private final Timeout task;
        
        public TimeoutTask(long startTime, Timeout task) {
            super();
            this.startTime = startTime;
            this.task = task;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public Timeout getTask() {
            return task;
        }
        
    }
    
    private int usage = 1;
    private final AtomicReference<TimeoutTask> lastTimeout = new AtomicReference<TimeoutTask>();
    private final ServiceManager serviceManager;

    public QueueTransferTask(ServiceManager serviceManager) {
        super();
        this.serviceManager = serviceManager;
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
        RTopic schedulerTopic = getTopic();
        statusListenerId = schedulerTopic.addListener(new BaseStatusListener() {
            @Override
            public void onSubscribe(String channel) {
                pushTask();
            }
        });
        
        messageListenerId = schedulerTopic.addListener(Long.class, new MessageListener<Long>() {
            @Override
            public void onMessage(CharSequence channel, Long startTime) {
                scheduleTask(startTime);
            }
        });
    }
    
    public void stop() {
        RTopic schedulerTopic = getTopic();
        schedulerTopic.removeListener(messageListenerId);
        schedulerTopic.removeListener(statusListenerId);
    }

    private void scheduleTask(final Long startTime) {
        TimeoutTask oldTimeout = lastTimeout.get();
        if (startTime == null) {
            return;
        }
        
        if (oldTimeout != null) {
            oldTimeout.getTask().cancel();
        }
        
        long delay = startTime - System.currentTimeMillis();
        if (delay > 10) {
            Timeout timeout = serviceManager.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    pushTask();
                    
                    TimeoutTask currentTimeout = lastTimeout.get();
                    if (currentTimeout.getTask() == timeout) {
                        lastTimeout.compareAndSet(currentTimeout, null);
                    }
                }
            }, delay, TimeUnit.MILLISECONDS);
            if (!lastTimeout.compareAndSet(oldTimeout, new TimeoutTask(startTime, timeout))) {
                timeout.cancel();
            }
        } else {
            pushTask();
        }
    }
    
    protected abstract RTopic getTopic();
    
    protected abstract RFuture<Long> pushTaskAsync();
    
    private void pushTask() {
        RFuture<Long> startTimeFuture = pushTaskAsync();
        startTimeFuture.whenComplete((res, e) -> {
            if (e != null) {
                if (serviceManager.isShuttingDown(e)) {
                    return;
                }
                log.error(e.getMessage(), e);
                scheduleTask(System.currentTimeMillis() + 5 * 1000L);
                return;
            }
            
            if (res != null) {
                scheduleTask(res);
            }
        });
    }

}
