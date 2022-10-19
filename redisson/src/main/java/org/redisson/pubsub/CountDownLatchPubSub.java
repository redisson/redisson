/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
package org.redisson.pubsub;

import org.redisson.RedissonCountDownLatchEntry;

import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class CountDownLatchPubSub extends PublishSubscribe<RedissonCountDownLatchEntry> {

    public static final Long ZERO_COUNT_MESSAGE = 0L;
    public static final Long NEW_COUNT_MESSAGE = 1L;
    
    public CountDownLatchPubSub(PublishSubscribeService service) {
        super(service);
    }

    @Override
    protected RedissonCountDownLatchEntry createEntry(CompletableFuture<RedissonCountDownLatchEntry> newPromise) {
        return new RedissonCountDownLatchEntry(newPromise);
    }

    @Override
    protected void onMessage(RedissonCountDownLatchEntry value, Long message) {
        if (message.equals(ZERO_COUNT_MESSAGE)) {
            Runnable runnableToExecute = value.getListeners().poll();
            if (runnableToExecute != null) {
                runnableToExecute.run();
            }

            value.getLatch().open();
        }
        if (message.equals(NEW_COUNT_MESSAGE)) {
            value.getLatch().close();
        }
    }

}
