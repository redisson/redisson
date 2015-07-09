package org.redisson.client.protocol;

public class PubSubPatternMessage {

    private final String pattern;
    private final String channel;
    private final Object value;

    public PubSubPatternMessage(String pattern, String channel, Object value) {
        super();
        this.pattern = pattern;
        this.channel = channel;
        this.value = value;
    }

    public String getPattern() {
        return pattern;
    }

    public String getChannel() {
        return channel;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "PubSubPatternMessage [pattern=" + pattern + ", channel=" + channel + ", value=" + value + "]";
    }

}
