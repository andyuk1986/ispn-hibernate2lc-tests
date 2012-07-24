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

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;
import java.io.File;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TransactionalISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String transactionalDbEntryName = "testnameTrans";

    private int rowCountInDb = 10000;
    private String lastRowName = "test10000";
    private String lastColRowName = "testCol10000";
    private int newCreateElementId = 10001;
    private String changedRowName = "testChanged";

    @PersistenceContext
    EntityManager manager;

    @Inject
    UserTransaction tx;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(TRANSACTIONAL_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(TRANSACTIONAL_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    @InSequence(1)
    @OperateOnDeployment("node1")
    public void testTransactionalInsertCaseNode1() throws Exception {
        DBEntry entry1 = new DBEntry("transactionalDBEntry", new Date());
        DBEntry entry2 = null;
        try {
            tx.begin();
            manager.persist(entry1);
            entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName(changedRowName);

            entry2 = (DBEntry)manager.merge(entry2);
            tx.commit();
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(elems, 3, expectedValues);
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testTransactionalInsertCaseNode2() throws Exception {
        DBEntry entry1 = new DBEntry("transactionalDBEntry", new Date());
        DBEntry entry2 = new DBEntry(changedRowName, new Date());
        DBEntryCollection col = new DBEntryCollection(lastColRowName, entry1);
        Set<DBEntryCollection> colSet = new HashSet<DBEntryCollection>();
        colSet.add(col);
        entry2.setCollection(colSet);

        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());

        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(newCreateElementId, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(elems, 3, expectedValues);

        //Rolling back
        tx.begin();
        entry2 = manager.find(DBEntry.class, rowCountInDb);
        entry2.setName(lastRowName);

        manager.merge(entry2);

        tx.commit();

        expectedValues.put(rowCountInDb, entry2);

        elems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(elems, 3, expectedValues);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testTransactionalInsertRollbackCaseNode1() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);
        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        try {
            //tx.begin();
            manager.persist(entry1);

            //Doing something non-acceptable for throwing an exception
            int someCalcValue = 5 / 0;

            DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName("aaaaaa");

            entry2 = manager.merge(entry2);
            //tx.commit();
        } catch(Exception ex) {
            System.out.println("Transaction have been rolled back!");
        }

        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 0, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testTransactionalInsertRollbackCaseNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 0, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());
    }
}
