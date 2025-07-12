package org.redisson.api;


/**
 * migrate mode
 *
 * @author lyrric
 */
public enum MigrateMode {

    /**
     * Default migrate
     */
    MIGRATE,
    /**
     * Do not remove the key from the local instance.
     */
    COPY,

    /**
     * Replace existing key on the remote instance.
     */
    REPLACE,


    COPY_REPLACE,
    ;

}
