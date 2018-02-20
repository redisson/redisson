package org.redisson.connection;

import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup;

/**
 * Created by hasaadon on 15/02/2018.
 */
public interface AddressResolverGroupFactory {
    DnsAddressResolverGroup create(Class<? extends DatagramChannel> channelType, DnsServerAddressStreamProvider nameServerProvider);

    AddressResolverGroupFactory ROUND_ROBIN_DNS_ADDRESS_RESOLVER_GROUP = new AddressResolverGroupFactory() {
        @Override
        public DnsAddressResolverGroup create(Class<? extends DatagramChannel> channelType, DnsServerAddressStreamProvider nameServerProvider) {
            return new RoundRobinDnsAddressResolverGroup(channelType, nameServerProvider);
        }
    };

    AddressResolverGroupFactory DNS_ADDRESS_RESOLVER_GROUP = new AddressResolverGroupFactory() {
        @Override
        public DnsAddressResolverGroup create(Class<? extends DatagramChannel> channelType, DnsServerAddressStreamProvider nameServerProvider) {
            return new DnsAddressResolverGroup(channelType, nameServerProvider);
        }
    };
}
