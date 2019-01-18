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
package org.redisson.jcache.bean;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class EmptyStatisticsMXBean extends JCacheStatisticsMXBean {

    @Override
    public void addEvictions(long value) {
    }
    
    @Override
    public void addGetTime(long value) {
    }
    
    @Override
    public void addHits(long value) {
    }
    
    @Override
    public void addMisses(long value) {
    }
    
    @Override
    public void addPuts(long value) {
    }
    
    @Override
    public void addPutTime(long value) {
    }
    
    @Override
    public void addRemovals(long value) {
    }
    
    @Override
    public void addRemoveTime(long value) {
    }
    
}
