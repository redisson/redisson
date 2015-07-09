package org.redisson.client.protocol.pubsub;

public class PubSubMessage {

    private final String channel;
    private final Object value;

    public PubSubMessage(String channel, Object value) {
        super();
        this.channel = channel;
        this.value = value;
    }

    public String getChannel() {
        return channel;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "PubSubMessage [channel=" + channel + ", value=" + value + "]";
    }

}
