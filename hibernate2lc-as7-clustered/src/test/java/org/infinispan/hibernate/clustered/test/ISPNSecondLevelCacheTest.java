package org.infinispan.hibernate.clustered.test;

import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.clustered.test.data.DBEntry;
import org.infinispan.hibernate.clustered.test.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.annotation.Resource;
import javax.naming.InitialContext;
import javax.persistence.*;
import javax.transaction.UserTransaction;
import java.util.*;

import static junit.framework.Assert.*;

/**
 * Tests for infinispan hibernate 2lc.
 */
public class ISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String dbEntryName = "testname";
    private String collectioName1 = "testCollection1";
    private String collectioName2 = "testCollection2";

    private int rowCountInDb = 999;
    private String lastRowName = "test999";
    private String lastColRowName = "testCol999";
    private int newCreateElementId = 1000;

    private static long firstExecutionDuration = 0;
    private static long secondExecutionDuration = 0;

    @Resource
    UserTransaction tx;

    @PersistenceContext
    EntityManager manager;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE2_WAR_NAME);
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
    public void testSecondLevelCacheForEntitiesAndCollectionsNode1() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        long startTime = System.currentTimeMillis();
        DBEntry entry = manager.find(DBEntry.class, rowCountInDb);
        long durationBeforeCache = System.currentTimeMillis() - startTime;

        assertNotNull(entry);
        assertEquals("The entry name should be test " + rowCountInDb, lastRowName, entry.getName());

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol " + rowCountInDb, lastColRowName, next.getName());
        }

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        entry = new DBEntry(lastRowName, new Date());
        DBEntryCollection collection1 = new DBEntryCollection(lastColRowName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection1);
        entry.setCollection(set);
        expectedElems.put(rowCountInDb, entry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 2, expectedElems);

        manager.close();
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForEntitiesAndCollectionsNode2() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        long startTime = System.currentTimeMillis();

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
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
        long durationBeforeCache = System.currentTimeMillis() - startTime;

        assertNotNull(entry);
        assertEquals("The entry name should be test " + rowCountInDb, lastRowName, entry.getName());

        set = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = set.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be testCol " + rowCountInDb, lastColRowName, next.getName());
        }

        manager.close();
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testDataInsertionNode1() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(cacheElems, 2, null);
        //Checking 2LC statistics
        DBEntry entry = new DBEntry(dbEntryName, new Date());
        manager.persist(entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        expectedElems.put(newCreateElementId, entry);

        assertCacheManagerStatistics(cacheElems, 2, null);

       /* entry = (DBEntry) manager.find(DBEntry.class, newCreateElementId);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Rolling back all changes
        manager.remove(entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 0, expectedElems);*/

        manager.close();
        emf.close();
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testDataInsertionNode2() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();


        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        DBEntry entry = (DBEntry) manager.find(DBEntry.class, newCreateElementId);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, expectedElems);

        //Rolling back all changes
        manager.remove(entry);

        cacheElems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cacheElems, 2, null);

        manager.close();
        emf.close();
    }

    @Test
    @InSequence(5)
    @OperateOnDeployment("node1")
    public void testDataUpdateNode1() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

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

    @Test
    @InSequence(6)
    @OperateOnDeployment("node2")
    public void testDataUpdateNode2() throws Exception {
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

    /*@Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForQueriesNode1() throws Exception {
        Query query = manager.createNamedQuery("listAllEntries");

        long startTime = System.currentTimeMillis();
        List<DBEntry> entries = query.getResultList();
        firstExecutionDuration = System.currentTimeMillis() - startTime;

        assertEquals("The book list should be 10000", rowCountInDb, entries.size());

        DBEntry lastEntry = entries.get(rowCountInDb - 1);
        assertEquals("The name of the last element should be predefined", lastRowName, lastEntry.getName());

        Set<DBEntryCollection> lastCol = lastEntry.getCollection();
        for (Iterator<DBEntryCollection> iterator = lastCol.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The name of the last element should be predefined", lastColRowName, next.getName());
        }

        System.out.println("The duration First time: " + firstExecutionDuration);

        manager.flush();

        //manager.getTransaction().commit();

        manager.close();
        //emf.close();
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForQueriesNode2() throws Exception {
        EmbeddedCacheManager cacheManager= getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> queryCache = cacheManager.getCache(QUERY_CACHE_NAME);
        assertCacheManagerStatistics(queryCache, 1, null);

        Map<CacheKey, CacheEntry> entityCache = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertEquals("The number of entities in the cache should be 2*" + rowCountInDb, 2 * rowCountInDb, entityCache.size());

        Query query = manager.createNamedQuery("listAllEntries");

        long startTime = System.currentTimeMillis();
        List<DBEntry> entries = query.getResultList();
        secondExecutionDuration = System.currentTimeMillis() - startTime;

        assertEquals("The book list should be " + rowCountInDb, rowCountInDb, entries.size());

        DBEntry lastEntry = entries.get(rowCountInDb - 1);
        assertEquals("The name of the last element should be predefined", lastRowName, lastEntry.getName());

        Set<DBEntryCollection> lastCol = lastEntry.getCollection();
        for (Iterator<DBEntryCollection> iterator = lastCol.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The name of the last element should be predefined", lastColRowName, next.getName());
        }

        System.out.println("The duration Second time: " + secondExecutionDuration);
        /*assertTrue("The duration second time should be much more lower: FIRST TIME EXECUTION: " + firstExecutionDuration
                + " SECOND TIME EXEC: " + secondExecutionDuration, firstExecutionDuration - secondExecutionDuration > 0);*/
   // }

    /*

    /*@Test
    public void testDataInsertion() throws NamingException {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        InitialContext ctx = new InitialContext();
        SessionFactory factory = (SessionFactory) ctx.lookup("SessionFactories/testSF");
        Statistics stat = factory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics("local-entity");
        SecondLevelCacheStatistics statisticsCol = stat.getSecondLevelCacheStatistics("local-collection");

        DBEntry entry = new DBEntry(dbEntryName, new Date());
        DBEntryCollection collection = new DBEntryCollection(collectioName1, entry);
        DBEntryCollection collection1 = new DBEntryCollection(collectioName2, entry);

        Set<DBEntryCollection> collectionSet = new HashSet<>();
        collectionSet.add(collection);
        collectionSet.add(collection1);

        entry.setCollection(collectionSet);

        //Checking 2LC statistics
        assertStatistics(statistics, 0, 0, 0);

        manager.persist(entry);

        assertStatistics(statistics, 0, 0, 0);
        assertStatistics(statisticsCol, 0, 0, 0);

        entry = manager.find(DBEntry.class, newCreateElementId);

        assertStatistics(statistics, 0, 0, 0);

        manager.remove(entry);
    }*/
}

