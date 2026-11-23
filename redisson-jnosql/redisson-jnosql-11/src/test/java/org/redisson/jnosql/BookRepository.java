package org.redisson.jnosql;

import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Repository;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Repository
public interface BookRepository extends CrudRepository<Book, String> {

}
