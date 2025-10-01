Data serialization is extensively used by Redisson to marshall and unmarshall bytes received or sent over network link with Redis or Valkey server. Many popular codecs are available for usage:  

Codec class name| Description
--- | ---
`org.redisson.codec.Kryo5Codec`| [Kryo 5](https://github.com/EsotericSoftware/kryo) binary codec<br/>(**Android** compatible)  __Default codec__  
`org.redisson.codec.KryoCodec`| [Kryo 4](https://github.com/EsotericSoftware/kryo) binary codec
`org.redisson.codec.JsonJacksonCodec`| [Jackson JSON](https://github.com/FasterXML/jackson) codec.<br/>Stores type information in `@class` field<br/>(**Android** compatible)  
`org.redisson.codec.TypedJsonJacksonCodec`| Jackson JSON codec which doesn't store type id (`@class` field)
`org.redisson.codec.AvroJacksonCodec`| [Avro](http://avro.apache.org/) binary json codec  
`org.redisson.codec.ProtobufCodec`| [Protobuf](https://github.com/protocolbuffers/protobuf) codec  
`org.redisson.codec.ForyCodec`| [Apache Fory](https://github.com/apache/fory) codec  
`org.redisson.codec.SmileJacksonCodec`| [Smile](http://wiki.fasterxml.com/SmileFormatSpec) binary json codec  
`org.redisson.codec.CborJacksonCodec`| [CBOR](http://cbor.io/) binary json codec  
`org.redisson.codec.MsgPackJacksonCodec`| [MsgPack](http://msgpack.org/) binary json codec  
`org.redisson.codec.IonJacksonCodec`| [Amazon Ion](https://amzn.github.io/ion-docs/) codec  
`org.redisson.codec.SerializationCodec`| JDK Serialization binary codec<br/>(**Android** compatible)
`org.redisson.codec.ZStdCodec`| [ZStandard](https://github.com/luben/zstd-jni) compression codec.<br/> Uses `Kryo5Codec` for serialization by default  
`org.redisson.codec.LZ4Codec`| [LZ4](https://github.com/jpountz/lz4-java) compression codec.<br/> Uses `Kryo5Codec` for serialization by default  
`org.redisson.codec.LZ4CodecV2`| [LZ4 Apache Commons](https://github.com/apache/commons-compress) compression codec.<br/> Uses `Kryo5Codec` for serialization by default  
`org.redisson.codec.SnappyCodecV2` | Snappy compression codec based on [snappy-java](https://github.com/xerial/snappy-java) project.<br/> Uses `Kryo5Codec` for serialization by default  
`org.redisson.codec.CompositeCodec` | Allows to mix different codecs as one  
`org.redisson.client.codec.StringCodec`| String codec  
`org.redisson.client.codec.LongCodec`| Long codec  
`org.redisson.client.codec.ByteArrayCodec` | Byte array codec