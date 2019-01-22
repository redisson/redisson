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
package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.cluster.ClusterNodeInfo;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.cluster.ClusterNodeInfo.Flag;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClusterNodesDecoder implements Decoder<List<ClusterNodeInfo>> {

    private final boolean ssl;
    
    public ClusterNodesDecoder(boolean ssl) {
        super();
        this.ssl = ssl;
    }

    @Override
    public List<ClusterNodeInfo> decode(ByteBuf buf, State state) throws IOException {
        String response = buf.toString(CharsetUtil.UTF_8);
        
        List<ClusterNodeInfo> nodes = new ArrayList<ClusterNodeInfo>();
        for (String nodeInfo : response.split("\n")) {
            ClusterNodeInfo node = new ClusterNodeInfo(nodeInfo);
            String[] params = nodeInfo.split(" ");

            String nodeId = params[0];
            node.setNodeId(nodeId);

            String flags = params[2];
            for (String flag : flags.split(",")) {
                String flagValue = flag.toUpperCase().replaceAll("\\?", "");
                node.addFlag(ClusterNodeInfo.Flag.valueOf(flagValue));
            }
            
            if (!node.containsFlag(Flag.NOADDR)) {
                String protocol = "redis://";
                if (ssl) {
                    protocol = "rediss://";
                }
                
                String addr = params[1].split("@")[0];
                String name = addr.substring(0, addr.lastIndexOf(":"));
                if (name.isEmpty()) {
                    // skip nodes with empty address
                    continue;
                }
                String uri = protocol + addr;
                node.setAddress(uri);
            }

            String slaveOf = params[3];
            if (!"-".equals(slaveOf)) {
                node.setSlaveOf(slaveOf);
            }

            if (params.length > 8) {
                for (int i = 0; i < params.length - 8; i++) {
                    String slots = params[i + 8];
                    if (slots.indexOf("-<-") != -1 || slots.indexOf("->-") != -1) {
                        continue;
                    }

                    String[] parts = slots.split("-");
                    if(parts.length == 1) {
                        node.addSlotRange(new ClusterSlotRange(Integer.valueOf(parts[0]), Integer.valueOf(parts[0])));
                    } else if(parts.length == 2) {
                        node.addSlotRange(new ClusterSlotRange(Integer.valueOf(parts[0]), Integer.valueOf(parts[1])));
                    }
                }
            }
            nodes.add(node);
        }
        return nodes;
    }

}
