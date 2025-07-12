package org.redisson.api.rkeys;

import org.redisson.api.MigrateMode;


/**
 *
 * @author lyrric
 */
public interface OptionalMigrateArgs extends MigrateArgs{


    /**
     * migrate mode
     * @param mode migrate mode
     * @return migrate conditions object
     */
    OptionalMigrateArgs mode(MigrateMode mode);

    /**
     * distinction username
     * @param username distinction username
     * @return migrate conditions object
     */
    OptionalMigrateArgs username(String username);

    /**
     * distinction password
     * @param password distinction password
     * @return migrate conditions object
     */
    OptionalMigrateArgs password(String password);

}
