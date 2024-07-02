/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBufUtil;
import org.redisson.misc.RandomXoshiro256PlusPlus;

import java.util.Random;

/**
 * Random identifier
 *
 * @author Nikita Koksharov
 *
 */
public class RandomIdGenerator implements IdGenerator {

    private static final Random random = RandomXoshiro256PlusPlus.create();

    @Override
    public String generateId() {
        byte[] id = new byte[16];
        random.nextBytes(id);
        return ByteBufUtil.hexDump(id);
    }

}
