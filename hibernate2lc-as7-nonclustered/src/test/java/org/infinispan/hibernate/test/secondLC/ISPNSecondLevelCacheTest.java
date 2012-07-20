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
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.*;

import static junit.framework.Assert.*;

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
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    public void testSecondLevelCacheForQueries() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map queryCache = cacheManager.getCache(QUERY_CACHE_NAME);

        InitialContext ctx = new InitialContext();
        SessionFactory factory = (SessionFactory) ctx.lookup("SessionFactories/testSF");
        Statistics stat = factory.getStatistics();

        assertCacheManagerStatistics(queryCache, 0, null);

        Query query = manager.createNamedQuery("listAllEntries");

        long startTime = System.currentTimeMillis();
        List<DBEntry> entries = query.getResultList();
        long durationFirstTime = System.currentTimeMillis() - startTime;

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

        query = manager.createNamedQuery("listAllEntries");
        startTime = System.currentTimeMillis();
        entries = query.getResultList();
        long durationSecondTime = System.currentTimeMillis() - startTime;

        assertTrue("The duration second time should be lower", durationFirstTime - durationSecondTime > 0 );

        assertQueryCacheStatistics(stat, 1, 1, 1);

        queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        manager.close();
        emf.close();
        factory.close();
    }

    @Test
    public void testSecondLevelCacheForEntitiesAndCollections() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        InitialContext ctx = new InitialContext();
        SessionFactory factory = (SessionFactory) ctx.lookup("SessionFactories/testSF");
        Statistics stat = factory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 0, null);

        long startTime = System.currentTimeMillis();
        DBEntry entry = manager.find(DBEntry.class, rowCountInDb);
        long durationBeforeCache = System.currentTimeMillis() - startTime;

        assertNotNull(entry);
        assertEquals("The entry name should be test10000", lastRowName, entry.getName());

        System.out.println(statistics.getPutCount() + " " + statistics.getMissCount() + " " + statistics.getHitCount());
        //Checking 2LC statistics
        assertStatistics(statistics, 2, 1, 0);
        assertStatistics(statisticsCol, 1, 0, 0);

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol10000", lastColRowName, next.getName());
        }

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        startTime = System.currentTimeMillis();
        entry = manager.find(DBEntry.class, rowCountInDb);
        long durationAfterCache = System.currentTimeMillis() - startTime;

        assertTrue("The duration of entity fetching should be less", durationBeforeCache - durationAfterCache > 0);
        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Checking 2LC statistics
        assertStatistics(statistics, 2, 1, 0);
        assertStatistics(statisticsCol, 1, 0, 0);

        manager.close();
        emf.close();
    }

    @Test
    public void testDataInsertion() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        InitialContext ctx = new InitialContext();
        SessionFactory factory = (SessionFactory) ctx.lookup("SessionFactories/testSF");
        Statistics stat = factory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(cacheElems, 0, null);
        //Checking 2LC statistics
        DBEntry entry = new DBEntry(dbEntryName, new Date());
        manager.persist(entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        //assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        entry = (DBEntry) manager.find(DBEntry.class, newCreateElementId);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 1, expectedElems);

        //Rolling back all changes
        manager.remove(entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 0, expectedElems);

        manager.close();
        emf.close();
    }

    @Test
    public void testDataUpdate() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        InitialContext ctx = new InitialContext();
        SessionFactory factory = (SessionFactory) ctx.lookup("SessionFactories/testSF");
        Statistics stat = factory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        EmbeddedCacheManager cacheManager = getCacheManager(emf);

        DBEntry entry = manager.find(DBEntry.class, rowCountInDb);

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        entry.setName("testulik");
        entry = manager.merge(entry);
        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Rolling back all actions
        entry.setName(lastRowName);
        entry = manager.merge(entry);

        manager.close();
        emf.close();
    }
}
