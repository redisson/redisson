package org.redisson.config;

/**
 * Adaptor for key value storage, a.k.a. Environment.
 */
@FunctionalInterface
interface EnvProvider {
    
    /**
     * Gets the value of the specified environment variable. An
     * environment variable is a system-dependent external named
     * value.
     *
     * @param name the name of the environment variable
     * @return the string value of the variable, or <code>null</code>
     * if the variable is not defined in the system environment
     */
    String get(String name);
    
}
