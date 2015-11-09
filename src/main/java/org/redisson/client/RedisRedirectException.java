package org.redisson.client;

import java.net.InetSocketAddress;
import java.net.URI;

class RedisRedirectException extends RedisException {

    private static final long serialVersionUID = 181505625075250011L;

    private int slot;
    private URI url;

    public RedisRedirectException(int slot, String url) {
        this.slot = slot;
        this.url = URI.create("//" + url);
    }

    public int getSlot() {
        return slot;
    }

    public URI getUrl() {
        return url;
    }

    public InetSocketAddress getAddr() {
        return new InetSocketAddress(url.getHost(), url.getPort());
    }

}
