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
package org.redisson.core;

import io.netty.util.concurrent.Future;

import java.util.Comparator;
import java.util.SortedSet;

public interface RSortedSet<V> extends SortedSet<V>, RObject {

    Future<Boolean> addAsync(V value);
    
    Future<Boolean> removeAsync(V value);
    
    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<? super V> comparator);

}
