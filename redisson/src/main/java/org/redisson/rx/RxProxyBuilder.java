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
package org.redisson.rx;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import org.redisson.api.RFuture;
import org.redisson.misc.ProxyBuilder;
import org.redisson.misc.ProxyBuilder.Callback;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RxProxyBuilder {

    public static <T> T create(CommandRxExecutor commandExecutor, Object instance, Class<T> clazz) {
        return create(commandExecutor, instance, null, clazz);
    }
    
    public static <T> T create(final CommandRxExecutor commandExecutor, Object instance, Object implementation, Class<T> clazz) {
        return ProxyBuilder.create(new Callback() {
            @Override
            public Object execute(final Method mm, final Object instance, final Object[] args) {
                return commandExecutor.flowable(new Callable<RFuture<Object>>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public RFuture<Object> call() {
                        try {
                            return (RFuture<Object>) mm.invoke(instance, args);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
            }
        }, instance, implementation, clazz);
    }
    
}
