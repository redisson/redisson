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
package org.redisson.reactive;

import org.redisson.RedissonVectorSet;
import org.redisson.api.RObject;
import org.redisson.api.RVectorSetAsync;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonVectorSetReactive {

    private static final int BATCH_SIZE = 10;

    private final RVectorSetAsync instance;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonVectorSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, new RedissonVectorSet(commandExecutor, name));
    }

    public RedissonVectorSetReactive(CommandReactiveExecutor commandExecutor, RVectorSetAsync instance) {
        this.commandExecutor = commandExecutor;
        this.instance = instance;
    }

    public String getName() {
        return ((RObject) instance).getName();
    }

    public Flux<String> iterator() {
        return fetchPage(null)
                .expand(page -> {
                    if (page.size() < BATCH_SIZE) {
                        return Mono.empty();
                    }
                    return fetchPage(page.get(page.size() - 1));
                })
                .concatMapIterable(page -> page);
    }

    private Mono<List<String>> fetchPage(String lastElement) {
        String start;
        if (lastElement == null) {
            start = "-";
        } else {
            start = "(" + lastElement;
        }
        return commandExecutor.reactive(() -> instance.rangeAsync(start, "+", BATCH_SIZE));
    }

}
