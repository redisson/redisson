/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.liveobject.resolver;

import org.redisson.RedissonAtomicLong;
import org.redisson.api.annotation.RId;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class LongGenerator implements RIdResolver<Long> {

    public static final LongGenerator INSTANCE = new LongGenerator();

    @Override
    public Long resolve(Class<?> value, RId id, String idFieldName, CommandAsyncExecutor commandAsyncExecutor) {
        if (commandAsyncExecutor instanceof CommandBatchService) {
            throw new IllegalStateException("this generator couldn't be used in batch");
        }

        return new RedissonAtomicLong(commandAsyncExecutor, this.getClass().getCanonicalName()
                + "{" + value.getCanonicalName() + "}:" + idFieldName).incrementAndGet();
    }

}
