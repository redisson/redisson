/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.quarkus.redisson.client.runtime.graal;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.quarkus.runtime.Quarkus;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.BooleanSupplier;

@TargetClass(InternetProtocolFamily.class)
final class InternetProtocolFamilySubstitutions {

    @Substitute
    public InetAddress localhost() {
        if (toString().equals("IPv4")) {
            return NetUtil.LOCALHOST4;
        } else if (toString().equals("IPv6")) {
            return NetUtil.LOCALHOST6;
        } else {
            throw new IllegalStateException("Unsupported internet protocol family: " + this);
        }
    }

}

final class Quarkus1Condition implements BooleanSupplier {

    @Override
    public boolean getAsBoolean() {
        return Quarkus.class.getPackage().getImplementationVersion().startsWith("1.");
    }
}

@TargetClass(value = NetUtil.class, onlyWith = Quarkus1Condition.class)
final class NetUtilSubstitutions {
    private NetUtilSubstitutions() {
    }

    @Alias
    @InjectAccessors(NetUtilLocalhost4Accessor.class)
    public static Inet4Address LOCALHOST4;

    @Alias
    @InjectAccessors(NetUtilLocalhost6Accessor.class)
    public static Inet6Address LOCALHOST6;

    @Alias
    @InjectAccessors(NetUtilLocalhostAccessor.class)
    public static InetAddress LOCALHOST;

    private static final class NetUtilLocalhost4Accessor {
        static Inet4Address get() {
            // using https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
            return NetUtilLocalhost4LazyHolder.LOCALHOST4;
        }

        static void set(Inet4Address ignored) {
            // a no-op setter to avoid exceptions when NetUtil is initialized at run-time
        }
    }

    private static final class NetUtilLocalhost4LazyHolder {
        private static final Inet4Address LOCALHOST4 = NetUtilInitializations.createLocalhost4();
    }

    private static final class NetUtilLocalhost6Accessor {
        static Inet6Address get() {
            // using https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
            return NetUtilLocalhost6LazyHolder.LOCALHOST6;
        }

        static void set(Inet6Address ignored) {
            // a no-op setter to avoid exceptions when NetUtil is initialized at run-time
        }
    }

    private static final class NetUtilLocalhost6LazyHolder {
        private static final Inet6Address LOCALHOST6 = NetUtilInitializations.createLocalhost6();
    }

    private static final class NetUtilLocalhostAccessor {
        static InetAddress get() {
            // using https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
            return NetUtilLocalhostLazyHolder.LOCALHOST;
        }

        static void set(InetAddress ignored) {
            // a no-op setter to avoid exceptions when NetUtil is initialized at run-time
        }
    }

    private static final class NetUtilLocalhostLazyHolder {
        private static final InetAddress LOCALHOST = NetUtilInitializations
                .determineLoopback(NetUtilLocalhost4LazyHolder.LOCALHOST4, NetUtilLocalhost6LazyHolder.LOCALHOST6)
                .address();
    }
}

final class NetUtilInitializations {
    /**
     * The logger being used by this class
     */
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NetUtilInitializations.class);

    private NetUtilInitializations() {
    }

    static Inet4Address createLocalhost4() {
        byte[] LOCALHOST4_BYTES = {127, 0, 0, 1};

        Inet4Address localhost4 = null;
        try {
            localhost4 = (Inet4Address) InetAddress.getByAddress("localhost", LOCALHOST4_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }

        return localhost4;
    }

    static Inet6Address createLocalhost6() {
        byte[] LOCALHOST6_BYTES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

        Inet6Address localhost6 = null;
        try {
            localhost6 = (Inet6Address) InetAddress.getByAddress("localhost", LOCALHOST6_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }

        return localhost6;
    }

    static NetworkIfaceAndInetAddress determineLoopback(Inet4Address localhost4, Inet6Address localhost6) {
        // Retrieve the list of available network interfaces.
        List<NetworkInterface> ifaces = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    // Use the interface with proper INET addresses only.
                    if (SocketUtils.addressesFromNetworkInterface(iface).hasMoreElements()) {
                        ifaces.add(iface);
                    }
                }
            }
        } catch (SocketException e) {
            logger.warn("Failed to retrieve the list of available network interfaces", e);
        }

        // Find the first loopback interface available from its INET address (127.0.0.1 or ::1)
        // Note that we do not use NetworkInterface.isLoopback() in the first place because it takes long time
        // on a certain environment. (e.g. Windows with -Djava.net.preferIPv4Stack=true)
        NetworkInterface loopbackIface = null;
        InetAddress loopbackAddr = null;
        loop: for (NetworkInterface iface: ifaces) {
            for (Enumeration<InetAddress> i = SocketUtils.addressesFromNetworkInterface(iface); i.hasMoreElements();) {
                InetAddress addr = i.nextElement();
                if (addr.isLoopbackAddress()) {
                    // Found
                    loopbackIface = iface;
                    loopbackAddr = addr;
                    break loop;
                }
            }
        }

        // If failed to find the loopback interface from its INET address, fall back to isLoopback().
        if (loopbackIface == null) {
            try {
                for (NetworkInterface iface: ifaces) {
                    if (iface.isLoopback()) {
                        Enumeration<InetAddress> i = SocketUtils.addressesFromNetworkInterface(iface);
                        if (i.hasMoreElements()) {
                            // Found the one with INET address.
                            loopbackIface = iface;
                            loopbackAddr = i.nextElement();
                            break;
                        }
                    }
                }

                if (loopbackIface == null) {
                    logger.warn("Failed to find the loopback interface");
                }
            } catch (SocketException e) {
                logger.warn("Failed to find the loopback interface", e);
            }
        }

        if (loopbackIface != null) {
            // Found the loopback interface with an INET address.
            logger.debug(
                    "Loopback interface: {} ({}, {})",
                    loopbackIface.getName(), loopbackIface.getDisplayName(), loopbackAddr.getHostAddress());
        } else {
            // Could not find the loopback interface, but we can't leave LOCALHOST as null.
            // Use LOCALHOST6 or LOCALHOST4, preferably the IPv6 one.
            if (loopbackAddr == null) {
                try {
                    if (NetworkInterface.getByInetAddress(localhost6) != null) {
                        logger.debug("Using hard-coded IPv6 localhost address: {}", localhost6);
                        loopbackAddr = localhost6;
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    if (loopbackAddr == null) {
                        logger.debug("Using hard-coded IPv4 localhost address: {}", localhost4);
                        loopbackAddr = localhost4;
                    }
                }
            }
        }

        return new NetworkIfaceAndInetAddress(loopbackIface, loopbackAddr);
    }

    static final class NetworkIfaceAndInetAddress {
        private final NetworkInterface iface;
        private final InetAddress address;

        NetworkIfaceAndInetAddress(NetworkInterface iface, InetAddress address) {
            this.iface = iface;
            this.address = address;
        }

        public NetworkInterface iface() {
            return iface;
        }

        public InetAddress address() {
            return address;
        }
    }
}
