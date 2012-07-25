package org.infinispan.hibernate.test.secondLC;

import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.hibernate.test.secondLC.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.persistence.*;
import javax.transaction.UserTransaction;
import java.util.*;

import static junit.framework.Assert.*;
import static junit.framework.Assert.assertEquals;

/**
 * Tests for infinispan hibernate 2lc.
 */
public class ISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String dbEntryName = "testname";

    private int rowCountInDb = 10000;
    private String lastRowName = "test10000";
    private String lastColRowName = "testCol10000";
    private int newCreateElementId = 10001;
    private int queryCacheSize = 500;


    @PersistenceContext
    EntityManager manager;

    @Inject
    UserTransaction tx;
    private String changedRowName = "testChanged";

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive jar = createInfinispan2LCWebArchive(WAR_NAME);
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    public void testSecondLevelCacheForQueries() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, QUERY_CACHE_NAME);

        tx.begin();

        Query query = manager.createNamedQuery("listAllEntries").setMaxResults(queryCacheSize);
        List<DBEntry> entries = query.getResultList();

        manager.flush();
        tx.commit();
        manager.clear();

        assertEquals("The book list should be 500", queryCacheSize, entries.size());

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 1, null);

        Map<CacheKey, CacheEntry> entityCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);

        //Entries with collections should be stored in the cache.
        assertCacheManagerStatistics(entityCacheMap, 2 * queryCacheSize, null);
    }

    @Test
    public void testSecondLevelCacheForEntitiesAndCollections() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);

        DBEntry entry = manager.find(DBEntry.class, rowCountInDb);

        assertNotNull(entry);
        assertEquals("The entry name should be test " + rowCountInDb, lastRowName, entry.getName());

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol " + rowCountInDb, lastColRowName, next.getName());
        }

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        entry = new DBEntry(lastRowName, new Date());
        DBEntryCollection collection1 = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection1);
        entry.setCollection(set);
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 2, expectedElems);
    }

    @Test
    public void testDataInsertion() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        DBEntry entry = new DBEntry(dbEntryName, new Date());
        tx.begin();
        manager.persist(entry);
        tx.commit();

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        tx.begin();

        entry = manager.find(DBEntry.class, newCreateElementId);
        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        //Rolling back all changes
        manager.remove(entry);
        tx.commit();

        assertCacheManagerStatistics(cacheElems, 0, null);
    }

    @Test
    public void testDataUpdate() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);

        tx.begin();
        DBEntry entry = manager.find(DBEntry.class, rowCountInDb);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        entry.setName(changedRowName);
        entry = manager.merge(entry);

        tx.commit();

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        tx.begin();
        //Rolling back all actions
        entry = manager.find(DBEntry.class, rowCountInDb);

        entry.setName(lastRowName);
        manager.merge(entry);

        tx.commit();
    }
}
