package org.redisson.liveobject.core;

import java.lang.reflect.Method;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import org.redisson.core.RMap;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class ExpirableInterceptor {

    @RuntimeType
    public static Object intercept(
            @Origin Method method,
            @AllArguments Object[] args,
            @FieldValue("liveObjectLiveMap") RMap map
    ) throws Exception {
        Class[] cls = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            cls[i] = args[i].getClass();
        }
        return RMap.class.getMethod(method.getName(), cls).invoke(map, args);
    }
}
