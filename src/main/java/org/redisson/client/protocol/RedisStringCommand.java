package org.redisson.client.protocol;

public class RedisStringCommand extends RedisCommand<String> {

    private Codec codec = new StringCodec();

    public RedisStringCommand(String name, int... encodeParamIndexes) {
        super(name, encodeParamIndexes);
    }

    public RedisStringCommand(String name, String subName, Decoder<String> reponseDecoder,
            int... encodeParamIndexes) {
        super(name, subName, reponseDecoder, encodeParamIndexes);
    }

    public RedisStringCommand(String name, String subName, int... encodeParamIndexes) {
        super(name, subName, encodeParamIndexes);
    }

    public RedisStringCommand(String name, Decoder<String> reponseDecoder, int... encodeParamIndexes) {
        super(name, reponseDecoder, encodeParamIndexes);
    }

    public Codec getCodec() {
        return codec;
    }

}
