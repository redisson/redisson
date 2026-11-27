package org.example.codec;

import org.redisson.client.codec.StringCodec;

/**
 * Codec living outside the {@code org.redisson} package, used to check that YAML
 * configs can still name custom codecs while the tag inspector is active.
 */
public class YamlTestCodec extends StringCodec {
}
