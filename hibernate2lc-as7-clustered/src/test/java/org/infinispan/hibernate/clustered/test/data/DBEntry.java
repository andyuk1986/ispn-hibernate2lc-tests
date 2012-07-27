package org.infinispan.hibernate.clustered.test.data;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

/**
 *
 */
@Entity
@Table(name = "entry")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL, region = "entityCache")
@NamedQueries(
        @NamedQuery(
                name = "listAllEntries",
                query = "from DBEntry",
                hints =  {
                        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
                        @QueryHint(name = "org.hibernate.cacheRegion", value = "queryCache")
                }
        )
)
public class DBEntry implements Serializable {
    private int id;
    private String name;
    private Date createDate;
    private Set<DBEntryCollection> collection;

    public DBEntry() {
    }

    public DBEntry(final String name, final Date createDate) {
        this.name = name;
        this.createDate = createDate;
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

    @Column(name = "create_date")
    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "entry")
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL, region = "collectionCache")
    public Set<DBEntryCollection> getCollection() {
        return collection;
    }

    public void setCollection(Set<DBEntryCollection> collection) {
        this.collection = collection;
    }

    public String toString() {
        return id + ":" + name;
    }
}
