package org.redisson.client.protocol.convertor;

public class IntegerReplayConvertor extends SingleConvertor<Integer> {

    @Override
    public Integer convert(Object obj) {
        return ((Long) obj).intValue();
    }

}
