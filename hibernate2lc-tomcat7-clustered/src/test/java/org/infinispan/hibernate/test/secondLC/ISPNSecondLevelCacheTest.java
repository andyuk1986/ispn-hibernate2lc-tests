package org.infinispan.hibernate.test.secondLC;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.hibernate.test.secondLC.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

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

    private String changedRowName = "changedRowName";

    private int queryLimit = 500;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(WAR_NAME);
        jar.addAsResource(HIBERNATE_CFG_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(WAR_NAME);
        jar.addAsResource(HIBERNATE_CFG_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    @InSequence(1)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForQueries() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        //Executing the query for the first  time.
        session.beginTransaction();
        Query query = session.getNamedQuery("listAllEntries").setMaxResults(queryLimit);

        Map<CacheKey, CacheEntry> queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 0, null);

        long startTime = System.currentTimeMillis();
        List<DBEntry> entries = query.list();
        long durationFirstTime = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        Map<CacheKey, CacheEntry> entityCache = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entityCache, 2 * queryLimit, null);

        //Executing query for the second time.
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        query = session.getNamedQuery("listAllEntries").setMaxResults(queryLimit);
        startTime = System.currentTimeMillis();
        entries = query.list();
        session.getTransaction().commit();

        long durationSecondTime = System.currentTimeMillis() - startTime;

        assertTrue("The duration second time should be lower", durationFirstTime - durationSecondTime > 0 );

        queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForQueriesNode2() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);

        Map<CacheKey, CacheEntry> queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        Map<CacheKey, CacheEntry> entityCache = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entityCache, 2 * queryLimit, null);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForEntitiesAndCollectionsNode1() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        cacheElems.clear();

        assertCacheManagerStatistics(cacheElems, 0, null);

        session.beginTransaction();
        long startTime = System.currentTimeMillis();
        DBEntry entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        long durationBeforeCache = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        assertNotNull(entry);
        assertEquals("The entry name should be test10000", lastRowName, entry.getName());

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol10000", lastColRowName, next.getName());
        }

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        startTime = System.currentTimeMillis();
        entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        long durationAfterCache = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        assertTrue("The duration of entity fetching should be less", durationBeforeCache - durationAfterCache > 0);
        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForEntitiesAndCollectionsNode2() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        DBEntry entry = new DBEntry(lastRowName, new Date());
        DBEntryCollection collection = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection);
        entry.setCollection(set);
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 2, expectedElems);
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment("node1")
    public void testDataInsertionNode1() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        cacheElems.clear();

        assertCacheManagerStatistics(cacheElems, 0, null);

        DBEntry entry = new DBEntry(dbEntryName, new Date());

        session.beginTransaction();
        session.persist(entry);
        session.getTransaction().commit();

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.get(DBEntry.class, newCreateElementId);
        session.getTransaction().commit();

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 1, expectedElems);
    }

    @Test
    @InSequence(6)
    @OperateOnDeployment("node2")
    public void testDataInsertionNode2() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        DBEntry entry = new DBEntry(dbEntryName, new Date());
        expectedElems.put(rowCountInDb, entry);

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        session.beginTransaction();
        entry = (DBEntry) session.get(DBEntry.class, newCreateElementId);
        session.getTransaction().commit();

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        //Rolling back all changes
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        session.delete(entry);
        session.getTransaction().commit();

        assertCacheManagerStatistics(cacheElems, 0, null);
    }

    @Test
    @InSequence(7)
    @OperateOnDeployment("node1")
    public void testDataUpdateNode1() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        cacheElems.clear();

        Session session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        DBEntry entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        session.getTransaction().commit();

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        entry.setName(changedRowName);
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.merge(entry);
        session.getTransaction().commit();

        assertEquals(changedRowName, entry.getName());

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);
    }

    @Test
    @InSequence(8)
    @OperateOnDeployment("node2")
    public void testDataUpdateNode2() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);

        DBEntry entry = new DBEntry(changedRowName, new Date());
        DBEntryCollection col = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> colSet = new HashSet<DBEntryCollection>();
        colSet.add(col);
        entry.setCollection(colSet);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Rolling back all actions
        Session session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        entry.setName(lastRowName);

        entry = (DBEntry) session.merge(entry);
        session.getTransaction().commit();

        expectedElems.put(rowCountInDb, entry);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);
    }
}
