/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.connection.dns;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStream;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;

/**
 * Workaround for https://github.com/netty/netty/issues/8261
 * 
 * @author Nikita Koksharov
 *
 */
public class MultiDnsAddressResolverGroup extends DnsAddressResolverGroup {

    private final List<DnsAddressResolverGroup> groups = new ArrayList<DnsAddressResolverGroup>();
    
    public MultiDnsAddressResolverGroup(
            Class<? extends DatagramChannel> channelType,
            DnsServerAddressStreamProvider nameServerProvider) {
        super(channelType, nameServerProvider);

        DnsServerAddressStream t = nameServerProvider.nameServerAddressStream("");
        InetSocketAddress firstDNS = t.next();
        while (true) {
            InetSocketAddress dns = t.next();
            DnsAddressResolverGroup group = new DnsAddressResolverGroup(channelType,
                                                    new SingletonDnsServerAddressStreamProvider(dns));
            groups.add(group);
            if (dns == firstDNS) {
                break;
            } 
        }
        
        // workaround for short DNS names
        groups.add(new DnsAddressResolverGroup(channelType, nameServerProvider));
    }
    
    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventLoop eventLoop,
            ChannelFactory<? extends DatagramChannel> channelFactory, DnsServerAddressStreamProvider nameServerProvider)
            throws Exception {
        List<AddressResolver<InetSocketAddress>> resolvers = new ArrayList<AddressResolver<InetSocketAddress>>();
        for (DnsAddressResolverGroup group : groups) {
            resolvers.add(group.getResolver(eventLoop));
        }
        return new GroupAddressResolver(resolvers);
    }
    
    @Override
    public void close() {
        for (DnsAddressResolverGroup group : groups) {
            group.close();
        }
    }

}
