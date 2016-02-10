/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.redisson.client.protocol.decoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;

public class ObjectMapReplayDecoder implements MultiDecoder<Map<Object, Object>> {

  @Override
  public Object decode(ByteBuf buf, State state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Object, Object> decode(List<Object> parts, State state) {
    Map<Object, Object> result = new HashMap<Object, Object>(parts.size() / 2);
    for (int i = 0; i < parts.size(); i++) {
      if (i % 2 != 0) {
        result.put(parts.get(i - 1), parts.get(i));
      }
    }
    return result;
  }

  @Override
  public boolean isApplicable(int paramNum, State state) {
    return false;
  }

}
