package org.infinispan.hibernate.test.secondLC;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cfg.Configuration;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.hibernate.test.secondLC.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.persistence.*;
import javax.transaction.UserTransaction;
import java.io.File;
import java.util.*;

import static junit.framework.Assert.*;

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

    @PersistenceContext
    EntityManager manager;

    @Deployment
    public static WebArchive createTransactionalDeployment() {
        WebArchive jar = createInfinispan2LCWebArchive(TRANSACTIONAL_WAR_NAME);
        jar.addAsResource(TRANSACTIONAL_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    @InSequence(1)
    public void testTransactionalInsertCase() throws Exception {
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

        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());

        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(elems, 3, expectedValues);
    }

    @Test
    @InSequence(2)
    public void testTransactionalInsertRollbackCase() throws Exception {
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

        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 0, null);

        DBEntry entry2 = manager.find(DBEntry.class, rowCountInDb);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());
    }
}
