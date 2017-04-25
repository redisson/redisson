package org.redisson.codec;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.ValueInstantiators.Base;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.avro.PackageVersion;

/**
 * Fix for https://github.com/FasterXML/jackson-databind/issues/1599
 * 
 * @author Nikita Koksharov
 * 
 * TODO remove after update to latest version of Jackson
 *
 */
public class DefenceModule extends SimpleModule {

    private static final long serialVersionUID = -429891510707420220L;

    public static class DefenceValueInstantiator extends Base {

        protected final static Set<String> DEFAULT_NO_DESER_CLASS_NAMES;
        static {
            Set<String> s = new HashSet<String>();
            // Courtesy of [https://github.com/kantega/notsoserial]:
            // (and wrt [databind#1599]
            s.add("org.apache.commons.collections.functors.InvokerTransformer");
            s.add("org.apache.commons.collections.functors.InstantiateTransformer");
            s.add("org.apache.commons.collections4.functors.InvokerTransformer");
            s.add("org.apache.commons.collections4.functors.InstantiateTransformer");
            s.add("org.codehaus.groovy.runtime.ConvertedClosure");
            s.add("org.codehaus.groovy.runtime.MethodClosure");
            s.add("org.springframework.beans.factory.ObjectFactory");
            s.add("com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl");
            DEFAULT_NO_DESER_CLASS_NAMES = Collections.unmodifiableSet(s);
        }
        
        @Override
        public ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription beanDesc,
                ValueInstantiator defaultInstantiator) {
            if (DEFAULT_NO_DESER_CLASS_NAMES.contains(beanDesc.getClassInfo().getRawType().getName())) {
                throw new IllegalArgumentException("Illegal type " + beanDesc.getClassInfo().getRawType().getName() + " to deserialize: prevented for security reasons");
            }
            
            return super.findValueInstantiator(config, beanDesc, defaultInstantiator);
        }
        
    }

    public DefenceModule() {
        super(PackageVersion.VERSION);
    }
    
    @Override
    public void setupModule(SetupContext context) {
        context.addValueInstantiators(new DefenceValueInstantiator());
    }

}
