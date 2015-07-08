package org.redisson.client.protocol;

public class PubSubMessage {

    public enum Type {SUBSCRIBE, MESSAGE}

    private Type type;
    private String channel;

    public PubSubMessage(Type type, String channel) {
        super();
        this.type = type;
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "PubSubReplay [type=" + type + ", channel=" + channel + "]";
    }

}
