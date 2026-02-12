/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api.keys;

import org.redisson.api.MigrateMode;


/**
 * OptionalMigrateArgs
 *
 * @author lyrric
 */
public interface OptionalMigrateArgs extends MigrateArgs {


    /**
     * Defines migrate mode
     * @see org.redisson.api.MigrateMode
     *
     * @param mode migrate mode
     * @return migrate conditions object
     */
    OptionalMigrateArgs mode(MigrateMode mode);

    /**
     * Defines username of destination instance
     * <p>
     * Authenticate with the given username to the remote instance.
     * <p>
     * if username is set, then password should be set too.
     * <p>
     * Redis 6 or greater ACL auth style
     *
     * @param username distinction username
     * @return migrate conditions object
     */
    OptionalMigrateArgs username(String username);

    /**
     * Defines password of destination instance
     * <p>
     * Authenticate with the given password to the remote instance.
     *
     * @param password distinction password
     * @return migrate conditions object
     */
    OptionalMigrateArgs password(String password);

}
