package org.redisson.hibernate;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Entity
@NamedQueries(@NamedQuery(name = "testQuery", query = "from ItemTransactional where name = :name"))
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL, region = "item")
@NaturalIdCache
public class ItemTransactional {

    @Id
    @GeneratedValue(generator = "increment")
    @GenericGenerator(name = "increment", strategy = "increment")
    private Long id;

    private String name;
    
    @NaturalId
    private String nid;
    
    @ElementCollection
    @JoinTable(name = "Entries", joinColumns = @JoinColumn(name="Item_id"))
    @Column(name = "entry", nullable = false)
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL, region = "item_entries")
    private List<String> entries = new ArrayList<String>();

    public ItemTransactional() {
    }

    public ItemTransactional(String name) {
        this.name = name;
    }

    public String getNid() {
        return nid;
    }

    public void setNid(String nid) {
        this.nid = nid;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getEntries() {
        return entries;
    }

}
