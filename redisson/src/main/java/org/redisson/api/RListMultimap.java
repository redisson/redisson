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
package org.redisson.api;

import java.util.List;

/**
 * List based Multimap. Stores insertion order and allows duplicates for values mapped to key.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RListMultimap<K, V> extends RMultimap<K, V> {

    /**
     * {@inheritDoc}
     *
     * <p>Because a {@code RListMultimap} may has duplicates among values mapped by key and stores insertion order
     * method returns a {@link List}, instead of the {@link java.util.Collection}
     * specified in the {@link RMultimap} interface.
     */
    @Override
    RList<V> get(K key);

    /**
     * {@inheritDoc}
     *
     * <p>Because a {@code RListMultimap} may has duplicates among values mapped by key and stores insertion order
     * method returns a {@link List}, instead of the {@link java.util.Collection}
     * specified in the {@link RMultimap} interface.
     */
    List<V> getAll(K key);

    /**
     * {@inheritDoc}
     *
     * <p>Because a {@code RListMultimap} may has duplicates among values mapped by key and stores insertion order
     * method returns a {@link List}, instead of the {@link java.util.Collection}
     * specified in the {@link RMultimap} interface.
     */
    @Override
    List<V> removeAll(Object key);

    /**
     * {@inheritDoc}
     *
     * <p>Because a {@code RListMultimap} may has duplicates among values mapped by key and stores insertion order
     * method returns a {@link List}, instead of the {@link java.util.Collection}
     * specified in the {@link RMultimap} interface.
     *
     */
    @Override
    List<V> replaceValues(K key, Iterable<? extends V> values);

}
