package org.redisson.client.protocol;

public interface Convertor<R> {

    R convert(Object obj);

}
