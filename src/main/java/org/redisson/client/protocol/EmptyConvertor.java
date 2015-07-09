package org.redisson.client.protocol;

public class EmptyConvertor<R> implements Convertor<R> {

    @Override
    public R convert(Object obj) {
        return (R) obj;
    }

}
