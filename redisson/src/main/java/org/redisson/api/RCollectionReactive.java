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

import org.reactivestreams.Publisher;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RCollectionReactive<V> extends RExpirableReactive {

    Publisher<V> iterator();

    Publisher<Boolean> retainAll(Collection<?> c);

    Publisher<Boolean> removeAll(Collection<?> c);

    Publisher<Boolean> contains(V o);

    Publisher<Boolean> containsAll(Collection<?> c);

    Publisher<Boolean> remove(V o);

    Publisher<Integer> size();

    Publisher<Integer> add(V e);

    Publisher<Integer> addAll(Publisher<? extends V> c);

    Publisher<Integer> addAll(Collection<? extends V> c);

}
