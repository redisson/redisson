package org.redisson.client.protocol.pubsub;

import java.util.List;

public class PubSubStatusMessage {

    public enum Type {SUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE, UNSUBSCRIBE}

    private final Type type;
    private final List<String> channels;

    public PubSubStatusMessage(Type type, List<String> channels) {
        super();
        this.type = type;
        this.channels = channels;
    }

    public List<String> getChannels() {
        return channels;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "PubSubStatusMessage [type=" + type + ", channels=" + channels + "]";
    }

}
