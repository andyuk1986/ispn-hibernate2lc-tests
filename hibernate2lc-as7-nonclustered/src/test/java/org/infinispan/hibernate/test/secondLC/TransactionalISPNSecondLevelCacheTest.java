package org.infinispan.hibernate.test.secondLC;

import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;
import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 *
 */
public class TransactionalISPNSecondLevelCacheTest extends AbstractISPNSecondLevelCacheTest {
    private String transactionalDbEntryName = "testnameTrans";
    private int rowCountInDb = 10000;
    private String lastRowName = "test10000";

    @PersistenceContext
    EntityManager manager;

    @Inject
    UserTransaction tx;
    private String changedRowName = "changedRowName";

    @Deployment
    public static WebArchive createTransactionalDeployment() {
        WebArchive jar = createInfinispan2LCWebArchive(WAR_NAME);
        jar.addAsResource(PERSISTENCE_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    public void testTransactionalInsertCase() throws Exception {
        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());
        DBEntry entry2 = null;
        try {
            tx.begin();
            entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName(changedRowName);
            entry2 = manager.merge(entry2);

            manager.persist(entry1);
            manager.flush();
            tx.commit();
            manager.clear();
        } catch(Exception ex) {
            ex.printStackTrace();
        }

        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();

        System.out.println("aaaaaaaaaaaaaa" + entry2);
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(elems, 3, expectedValues);

        //Rolling back the changes
        try {
            tx.begin();
            entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName(lastRowName);
            manager.merge(entry2);

            manager.flush();
            tx.commit();
            manager.clear();
        } catch(Exception ex) {
            ex.printStackTrace();
        }
    }

    @Test
    public void testTransactionalInsertRollbackCase() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        try {
            tx.begin();
            manager.persist(entry1);

            //Doing something non-acceptable for throwing an exception
            int someCalcValue = 5 / 0;

            DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
            entry2.setName("aaaaaa");

            entry2 = manager.merge(entry2);
            tx.commit();
        } catch(Exception ex) {
            tx.rollback();
            System.out.println("Transaction have been rolled back!");
        }

        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 0, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());
    }
}
