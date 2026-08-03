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
package org.redisson.rx;

import org.redisson.api.RMaps;
import org.redisson.api.RMapsImportRx;
import org.redisson.api.map.MapsImportArgs;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> field type
 * @param <V> value type
 */
public class RedissonMapsRx<K, V> {

    private final RMaps<K, V> instance;
    private final CommandRxExecutor commandExecutor;

    public RedissonMapsRx(RMaps<K, V> instance, CommandRxExecutor commandExecutor) {
        this.instance = instance;
        this.commandExecutor = commandExecutor;
    }

    public RMapsImportRx<K, V> createImport(MapsImportArgs<K> args) {
        return RxProxyBuilder.create(commandExecutor, instance.createImport(args), RMapsImportRx.class);
    }

}
