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
package org.redisson.spring.data.connection;

import java.util.concurrent.TimeUnit;

import org.redisson.client.protocol.convertor.Convertor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SecondsConvertor implements Convertor<Long> {

    private final TimeUnit unit;
    private final TimeUnit source;
    
    public SecondsConvertor(TimeUnit unit, TimeUnit source) {
        super();
        this.unit = unit;
        this.source = source;
    }

    @Override
    public Long convert(Object obj) {
        return unit.convert((Long)obj, source);
    }

}
