package org.redisson.config;

/**
 * Default implementation for environment values adaptor.
 */
public class SystemEnvProvider implements EnvProvider {
    
    /**
     * @see System#getenv()
     */
    @Override
    public String get(String name) {
        return System.getenv(name);
    }
    
}
