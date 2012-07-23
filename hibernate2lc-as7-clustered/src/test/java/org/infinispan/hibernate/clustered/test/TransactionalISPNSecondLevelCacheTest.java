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
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.io.File;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TransactionalISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String transactionalDbEntryName = "testnameTrans";

    private String dbEntryName = "testname";
    private String collectioName1 = "testCollection1";
    private String collectioName2 = "testCollection2";

    private int rowCountInDb = 10000;
    private String lastRowName = "test10000";
    private String lastColRowName = "testCol10000";
    private int newCreateElementId = 10001;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
       /* jar.addAsResource(TRANSACTIONAL_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)*/
                jar.addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE2_WAR_NAME);
        /*jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)*/
                jar.addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    public void test() {

    }

    /*@Test
    @InSequence(1)
    @OperateOnDeployment("node1")
    public void testTransactionalInsertCaseNode1() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        DBEntry entry2 = null;
        try {
            manager.persist(entry1);
            entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName("testulik");

            entry2 = (DBEntry)manager.merge(entry2);

        } catch(Exception ex) {
            ex.printStackTrace();
        }

        EmbeddedCacheManager cacheManager = getCacheManager(emf);

        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(elems, 3, expectedValues);

        manager.close();
        emf.close();
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testTransactionalInsertCaseNode2() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        DBEntry entry2 = new DBEntry(lastRowName, new Date());
        DBEntryCollection col = new DBEntryCollection(lastColRowName, entry1);
        Set<DBEntryCollection> colSet = new HashSet<DBEntryCollection>();
        colSet.add(col);
        entry2.setCollection(colSet);

        EmbeddedCacheManager cacheManager = getCacheManager(emf);

        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(elems, 3, expectedValues);

        manager.close();
        emf.close();
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node2")
    public void testTransactionalInsertRollbackCaseNode1() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        try {
            manager.persist(entry1);

            //Doing something non-acceptable for throwing an exception
            int someCalcValue = 5 / 0;

            DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName("aaaaaa");

            entry2 = manager.merge(entry2);
        } catch(Exception ex) {
            System.out.println("Transaction have been rolled back!");
        }

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 2, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());

        manager.close();
        emf.close();
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testTransactionalInsertRollbackCaseNode2() throws Exception {
        EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
        EntityManager manager = emf.createEntityManager();

        EmbeddedCacheManager cacheManager = getCacheManager(emf);
        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 2, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());

        manager.close();
        emf.close();
    }*/
}
