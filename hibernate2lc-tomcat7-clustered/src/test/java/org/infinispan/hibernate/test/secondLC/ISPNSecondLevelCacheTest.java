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

    @Deployment
    public static WebArchive createDeployment() {
        WebArchive jar = createInfinispan2LCWebArchive(WAR_NAME);
        jar.addAsResource(HIBERNATE_CFG_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsResource(WEB_XML_PATH);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    public void testSecondLevelCacheForQueries() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();
        Statistics stat = sessionFactory.getStatistics();

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        //Executing the query for the first  time.
        session.beginTransaction();
        Query query = session.getNamedQuery("listAllEntries");

        Map<CacheKey, CacheEntry> queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 0, null);

        long startTime = System.currentTimeMillis();
        List<DBEntry> entries = query.list();
        long durationFirstTime = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        assertEquals("The book list should be 10000", rowCountInDb, entries.size());

        DBEntry lastEntry = entries.get(rowCountInDb - 1);
        assertEquals("The name of the last element should be predefined", lastRowName, lastEntry.getName());

        Set<DBEntryCollection> lastCol = lastEntry.getCollection();
        for (Iterator<DBEntryCollection> iterator = lastCol.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The name of the last element should be predefined", lastColRowName, next.getName());
        }

        assertQueryCacheStatistics(stat, 0, 1, 1);
        queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        //Executing query for the second time.
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        query = session.getNamedQuery("listAllEntries");
        startTime = System.currentTimeMillis();
        entries = query.list();
        session.getTransaction().commit();

        long durationSecondTime = System.currentTimeMillis() - startTime;

        assertTrue("The duration second time should be lower", durationFirstTime - durationSecondTime > 0 );

        assertQueryCacheStatistics(stat, 1, 1, 1);
        queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);
    }

    @Test
    public void testSecondLevelCacheForEntitiesAndCollections() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();
        Statistics stat = sessionFactory.getStatistics();

        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 0, null);

        session.beginTransaction();
        long startTime = System.currentTimeMillis();
        DBEntry entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        long durationBeforeCache = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        assertNotNull(entry);
        assertEquals("The entry name should be test10000", lastRowName, entry.getName());

        //Checking 2LC statistics
        assertStatistics(statistics, 2, 1, 0);
        assertStatistics(statisticsCol, 1, 0, 0);
        assertTrue("The cache should be empty", cacheElems.size() == 2);

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol10000", lastColRowName, next.getName());
        }

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        startTime = System.currentTimeMillis();
        entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        long durationAfterCache = System.currentTimeMillis() - startTime;
        session.getTransaction().commit();

        assertTrue("The duration of entity fetching should be less", durationBeforeCache - durationAfterCache > 0);
        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Checking 2LC statistics
        assertStatistics(statistics, 2, 1, 2);
        assertStatistics(statisticsCol, 1, 0, 1);
    }

    @Test
    public void testDataInsertion() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();
        Statistics stat = sessionFactory.getStatistics();

        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(cacheElems, 0, null);
        //Checking 2LC statistics
        assertStatistics(statistics, 0, 0, 0);

        DBEntry entry = new DBEntry(dbEntryName, new Date());

        session.beginTransaction();
        session.persist(entry);
        session.getTransaction().commit();

        assertStatistics(statistics, 1, 0, 0);

        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.get(DBEntry.class, newCreateElementId);
        session.getTransaction().commit();

        assertStatistics(statistics, 1, 0, 1);

        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        //Rolling back all changes
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        session.delete(entry);
        session.getTransaction().commit();

        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 0, expectedElems);
    }

    @Test
    public void testDataUpdate() {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Session session = sessionFactory.getCurrentSession();
        Statistics stat = sessionFactory.getStatistics();

        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);

        session.beginTransaction();
        DBEntry entry = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        session.getTransaction().commit();

        assertStatistics(statistics, 2, 1, 0);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        entry.setName("testulik");
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.merge(entry);
        session.getTransaction().commit();

        //As the merge method updates the db and returns the newly updated record, then the numbers should be as following:
        // 3 puts - 2 were before, now the new updated record is added
        // 1 miss - as before
        //2 hits -
        assertEquals("testulik", entry.getName());
        assertStatistics(statistics, 3, 1, 2);
        assertStatistics(statisticsCol, 1, 0, 1);

        cacheElems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Rolling back all actions
        entry.setName(lastRowName);
        session = sessionFactory.getCurrentSession();
        session.beginTransaction();
        entry = (DBEntry) session.merge(entry);
        session.getTransaction().commit();
    }
}
