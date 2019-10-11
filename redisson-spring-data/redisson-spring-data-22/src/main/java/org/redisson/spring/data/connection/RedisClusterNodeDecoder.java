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

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.misc.RedisURI;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisClusterNode.Flag;
import org.springframework.data.redis.connection.RedisClusterNode.LinkState;
import org.springframework.data.redis.connection.RedisClusterNode.RedisClusterNodeBuilder;
import org.springframework.data.redis.connection.RedisClusterNode.SlotRange;
import org.springframework.data.redis.connection.RedisNode.NodeType;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisClusterNodeDecoder implements Decoder<List<RedisClusterNode>> {

    @Override
    public List<RedisClusterNode> decode(ByteBuf buf, State state) throws IOException {
        String response = buf.toString(CharsetUtil.UTF_8);
        
        List<RedisClusterNode> nodes = new ArrayList<RedisClusterNode>();
        for (String nodeInfo : response.split("\n")) {
            String[] params = nodeInfo.split(" ");

            String nodeId = params[0];

            String flagsStr = params[2];
            Set<Flag> flags = EnumSet.noneOf(Flag.class);
            for (String flag : flagsStr.split(",")) {
                String flagValue = flag.toUpperCase().replaceAll("\\?", "");
                flags.add(Flag.valueOf(flagValue));
            }
            
            RedisURI address = null;
            if (!flags.contains(Flag.NOADDR)) {
                String addr = params[1].split("@")[0];
                address = new RedisURI("redis://" + addr);
            }

            String masterId = params[3];
            if ("-".equals(masterId)) {
                masterId = null;
            }

            Set<Integer> slotsCollection = new HashSet<Integer>();
            LinkState linkState = null;
            if (params.length >= 8 && params[7] != null) {
                linkState = LinkState.valueOf(params[7].toUpperCase());
            }
            if (params.length > 8) {
                for (int i = 0; i < params.length - 8; i++) {
                    String slots = params[i + 8];
                    if (slots.indexOf("-<-") != -1 || slots.indexOf("->-") != -1) {
                        continue;
                    }

                    String[] parts = slots.split("-");
                    if(parts.length == 1) {
                        slotsCollection.add(Integer.valueOf(parts[0]));
                    } else if(parts.length == 2) {
                        for (int j = Integer.valueOf(parts[0]); j < Integer.valueOf(parts[1]) + 1; j++) {
                            slotsCollection.add(j);
                        }
                    }
                }
            }
            
            NodeType type = null;
            if (flags.contains(Flag.MASTER)) {
                type = NodeType.MASTER;
            } else if (flags.contains(Flag.SLAVE)) {
                type = NodeType.SLAVE;
            }
            
            RedisClusterNodeBuilder builder = RedisClusterNode.newRedisClusterNode()
                    .linkState(linkState)
                    .slaveOf(masterId)
                    .serving(new SlotRange(slotsCollection))
                    .withId(nodeId)
                    .promotedAs(type)
                    .withFlags(flags);
            
            if (address != null) {
                builder.listeningAt(address.getHost(), address.getPort());
            }
            nodes.add(builder.build());
        }
        return nodes;
    }

}
