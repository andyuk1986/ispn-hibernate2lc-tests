package org.infinispan.hibernate.clustered.test;

import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.infinispan.hibernate.clustered.test.data.DBEntry;
import org.infinispan.hibernate.clustered.test.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.transaction.UserTransaction;
import java.util.*;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

/**
 * Tests for infinispan hibernate 2lc.
 */
public class ISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String dbEntryName = "testname";

    private int rowCountInDb = 10000;
    private String lastRowName = "test10000";
    private String lastColRowName = "testCol10000";
    private int newCreateElementId = 10001;

    private String changedRowName = "testChanged";

    private int queryCacheSize = 500;


    @PersistenceContext
    EntityManager manager;

    @Inject
    UserTransaction tx;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    @InSequence(1)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForQueriesNode1() throws Exception {
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
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForQueriesNode2() throws Exception {
        EmbeddedCacheManager cacheManager= getCacheManager(manager.getEntityManagerFactory());

        Map<CacheKey, CacheEntry> queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        Map<CacheKey, CacheEntry> entityCache = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entityCache, 2 * queryCacheSize, null);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForEntitiesAndCollectionsNode1() throws Exception {
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
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForEntitiesAndCollectionsNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        DBEntry entry = new DBEntry(lastRowName, new Date());
        DBEntryCollection collection = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection);
        entry.setCollection(set);
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 2, expectedElems);

        entry = manager.find(DBEntry.class, rowCountInDb);

        assertNotNull(entry);
        assertEquals("The entry name should be test " + rowCountInDb, lastRowName, entry.getName());

        set = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = set.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol " + rowCountInDb, lastColRowName, next.getName());
        }
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment("node1")
    public void testDataInsertionNode1() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        DBEntry entry = new DBEntry(dbEntryName, new Date());
        tx.begin();
        manager.persist(entry);
        tx.commit();

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment("node2")
    public void testDataInsertionNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        DBEntry entry = new DBEntry(dbEntryName, new Date());
        expectedElems.put(rowCountInDb, entry);

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
    @InSequence(7)
    @OperateOnDeployment("node1")
    public void testDataUpdateNode1() throws Exception {
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
    }

    @Test
    @InSequence(8)
    @OperateOnDeployment("node2")
    public void testDataUpdateNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());

        DBEntry entry = new DBEntry(changedRowName, new Date());
        DBEntryCollection col = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> colSet = new HashSet<DBEntryCollection>();
        colSet.add(col);
        entry.setCollection(colSet);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        tx.begin();
        //Rolling back all actions
        entry = manager.find(DBEntry.class, rowCountInDb);

        entry.setName(lastRowName);
        manager.merge(entry);

        tx.commit();
    }
}

