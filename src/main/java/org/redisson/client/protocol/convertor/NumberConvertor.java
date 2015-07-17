package org.redisson.client.protocol.convertor;

import java.math.BigDecimal;

public class NumberConvertor extends SingleConvertor<Object> {

    private Class<?> resultClass;

    public NumberConvertor(Class<?> resultClass) {
        super();
        this.resultClass = resultClass;
    }

    @Override
    public Object convert(Object result) {
        String res = (String) result;
        if (resultClass.isAssignableFrom(Long.class)) {
            Object obj = Long.parseLong(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Integer.class)) {
            Object obj = Integer.parseInt(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Float.class)) {
            Object obj = Float.parseFloat(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(Double.class)) {
            Object obj = Double.parseDouble(res);
            return obj;
        }
        if (resultClass.isAssignableFrom(BigDecimal.class)) {
            Object obj = new BigDecimal(res);
            return obj;
        }
        throw new IllegalStateException("Wrong value type!");
    }

}
