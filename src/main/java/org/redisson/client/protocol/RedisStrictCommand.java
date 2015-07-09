package org.redisson.client.protocol;

public class RedisStrictCommand<T> extends RedisCommand<T> {

    public RedisStrictCommand(String name, MultiDecoder<T> replayMultiDecoder, int... encodeParamIndexes) {
        super(name, replayMultiDecoder, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, int... encodeParamIndexes) {
        super(name, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, Convertor<T> convertor, int ... encodeParamIndexes) {
        super(name, convertor, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, String subName, Decoder<T> reponseDecoder,
            int... encodeParamIndexes) {
        super(name, subName, reponseDecoder, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, String subName, int... encodeParamIndexes) {
        super(name, subName, encodeParamIndexes);
    }

    public RedisStrictCommand(String name, Decoder<T> reponseDecoder, int... encodeParamIndexes) {
        super(name, reponseDecoder, encodeParamIndexes);
    }

}
