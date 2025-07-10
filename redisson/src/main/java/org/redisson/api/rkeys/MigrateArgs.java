package org.redisson.api.rkeys;

/**
 *
 * @author lyrric
 */
public interface MigrateArgs {

    /**
     * keys to transfer Redis version >= 3.0.6
     * @param keys keys to migrate
     * @return migrate conditions object
     */
    static OptionalMigrateArgs keys(String ...keys){return new MigrateArgsParams(keys);}

    /**
     * distinction host
     * @param host distinction host
     * @return migrate conditions object
     */
    OptionalMigrateArgs host(String host);

    /**
     * distinction port
     * @param port distinction port
     * @return migrate conditions object
     */
    OptionalMigrateArgs port(int port);

    /**
     * distinction database
     * @param database distinction database
     * @return migrate conditions object
     */
    OptionalMigrateArgs database(int database);

    /**
     * timeout
     * @param timeout timeout
     * @return migrate conditions object
     */
    OptionalMigrateArgs timeout(long timeout);


}
