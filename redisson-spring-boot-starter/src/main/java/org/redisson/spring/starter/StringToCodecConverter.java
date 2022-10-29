package org.redisson.spring.starter;

import org.redisson.client.codec.Codec;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;

@Component
@ConfigurationPropertiesBinding
public class StringToCodecConverter implements Converter<String, Codec> {
    @Override
    public Codec convert(String codecClassName) {
        try {
            Class<Codec> clazz = (Class<Codec>) Class.forName(codecClassName);
            return clazz.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
