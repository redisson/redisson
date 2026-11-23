package org.redisson.jnosql;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Entity
public class Book {

    @Id
    private String isbn;

    @Column
    private String title;

    @Column
    private int year;

    public Book() {
    }

    public Book(String isbn, String title, int year) {
        this.isbn = isbn;
        this.title = title;
        this.year = year;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public int getYear() {
        return year;
    }

}
