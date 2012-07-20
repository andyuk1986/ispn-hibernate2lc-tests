package org.infinispan.hibernate.clustered.test.data;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

/**
 *
 */
@Entity
@Table(name = "entry_col")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL, region = "replicated-entity")
public class DBEntryCollection {
    private int id;
    private String name;
    private DBEntry entry;

    public DBEntryCollection() {
    }

    public DBEntryCollection(String name, DBEntry entry) {
        this.name = name;
        this.entry = entry;
    }

    @Id
    @GeneratedValue(generator="increment")
    @GenericGenerator(name="increment", strategy = "increment")
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Column(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entry_id", nullable = false)
    public DBEntry getEntry() {
        return entry;
    }

    public void setEntry(DBEntry entry) {
        this.entry = entry;
    }
}
