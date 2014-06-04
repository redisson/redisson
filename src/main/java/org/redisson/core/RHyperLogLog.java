/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import io.netty.util.concurrent.Future;

import java.util.Collection;

public interface RHyperLogLog<V> extends RObject {

    long add(V obj);

    long addAll(Collection<V> objects);

    long count();

    long countWith(String ... otherLogNames);

    long mergeWith(String ... otherLogNames);

    Future<Long> addAsync(V obj);

    Future<Long> addAllAsync(Collection<V> objects);

    Future<Long> countAsync();

    Future<Long> countWithAsync(String ... otherLogNames);

    Future<Long> mergeWithAsync(String ... otherLogNames);

}
