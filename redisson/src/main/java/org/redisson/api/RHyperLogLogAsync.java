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
package org.redisson.api;

import java.util.Collection;

/**
 * Probabilistic data structure that lets you maintain counts of millions of items with extreme space efficiency.
 * Asynchronous interface.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RHyperLogLogAsync<V> extends RExpirableAsync {

    RFuture<Boolean> addAsync(V obj);

    RFuture<Boolean> addAllAsync(Collection<V> objects);

    RFuture<Long> countAsync();

    RFuture<Long> countWithAsync(String ... otherLogNames);

    RFuture<Void> mergeWithAsync(String ... otherLogNames);

}
