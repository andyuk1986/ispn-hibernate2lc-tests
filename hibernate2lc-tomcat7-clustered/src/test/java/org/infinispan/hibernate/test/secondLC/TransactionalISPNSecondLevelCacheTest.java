package org.infinispan.hibernate.test.secondLC;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import javax.naming.InitialContext;
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

    @Deployment
    public static WebArchive createTransactionalDeployment() {
        WebArchive jar = createInfinispan2LCWebArchive(TRANSACTIONAL_WAR_NAME);
        jar.addAsResource(TRANSACTIONAL_HIBERNATE_CFG_XML, HIBERNATE_CFG_URL)
                .addAsResource(TRANSACTIONAL_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME)
                .addAsManifestResource(CONTEXT_XML_PATH)
                .addAsLibrary(new File("target/test-libs/jbossjta-4.16.4.Final.jar"));

        System.out.println(jar.toString(true));

        return jar;
    }

    private UserTransaction openTransaction() throws Exception {
        UserTransaction tx = (UserTransaction)new InitialContext().lookup("java:comp/UserTransaction");
        tx.begin();

        return tx;
    }

    private void commitTransaction(final UserTransaction tx) throws Exception{
        tx.commit();
    }

    @Test
    @InSequence(1)
    public void testTransactionalInsertCase() throws Exception {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Statistics stat = sessionFactory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics(LOCAL_ENTITY_CACHE_NAME);

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        UserTransaction transaction = openTransaction();
        Session session = sessionFactory.getCurrentSession();
        DBEntry entry2 = null;
        try {
            //Adds new instance to Hibernate 2LC +1
            session.persist(entry1);

            //Checks whether this instance is located in the cache (hit = 0), as there is no instance of it in the cache
            //it will add another instance to the cache +1
            //As the collection is loaded eagerly, and one DBEntryCollection is attached to this entry, it will also be
            //added to the cache +1
            entry2 = (DBEntry) session.get(DBEntry.class, rowCountInDb);
            entry2.setName("testulik");

            entry2 = (DBEntry)session.merge(entry2);

            commitTransaction(transaction);
        } catch(Exception ex) {
            ex.printStackTrace();
            transaction.rollback();
        }
        System.out.println(statistics.getPutCount() + "       " + statistics.getHitCount() + "         " + statistics.getMissCount());

        //assertStatistics(statistics, 4, 1, 0);

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);

        Map<Integer, DBEntry> expectedValues = new HashMap<Integer, DBEntry>();
        expectedValues.put(rowCountInDb, entry2);
        expectedValues.put(10001, entry1);

        Map<CacheKey, CacheEntry> elems = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(elems, 3, expectedValues);
        sessionFactory.close();
    }

    @Test
    @InSequence(2)
    public void testTransactionalInsertRollbackCase() throws Exception {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();
        Statistics stat = sessionFactory.getStatistics();
        SecondLevelCacheStatistics statistics = stat.getSecondLevelCacheStatistics(LOCAL_ENTITY_CACHE_NAME);

        DBEntry entry1 = new DBEntry(transactionalDbEntryName, new Date());

        UserTransaction transaction = openTransaction();
        Session session = sessionFactory.getCurrentSession();

        try {
            //Should add the instance to the cache, but as the transaction would be rolled back, it is not added.
            session.persist(entry1);

            //Doing something non-acceptable for throwing an exception
            int someCalcValue = 5 / 0;

            DBEntry entry2 = (DBEntry) session.get(DBEntry.class, rowCountInDb);
            entry2.setName("aaaaaaaa");

            entry2 = (DBEntry)session.merge(entry2);

            commitTransaction(transaction);
        } catch(Exception ex) {
            transaction.rollback();
            System.out.println("Transaction have been rolled back!");
        }

        EmbeddedCacheManager cacheManager = getCacheManager(sessionFactory);
        Map<CacheKey, CacheEntry> entryCacheMap = cacheManager.getCache(LOCAL_ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(entryCacheMap, 0, null);

        transaction = openTransaction();
        session = sessionFactory.getCurrentSession();
        DBEntry entry2 = (DBEntry) session.get(DBEntry.class, rowCountInDb);
        commitTransaction(transaction);
        assertEquals("The name of entity should not be changed.", lastRowName, entry2.getName());

        sessionFactory.close();
    }

   /* @Test
    @InSequence(3)
    public void testTransactionalInsert() throws Exception {
        SessionFactory sessionFactory = new Configuration().configure().buildSessionFactory();

        TransactionalExecutor executor = new TransactionalExecutor(sessionFactory, transactionalEntryId1, transactionalDbEntryName, transactionalCollectioName1);
        TransactionalExecutor executor1 = new TransactionalExecutor(sessionFactory, transactionalEntryId2, transactionalDbEntryName1, transactionalCollectionName2);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, 5);

        Timer timer = new Timer();
        timer.schedule(executor, cal.getTime());

        Timer timer1 = new Timer();
        timer1.schedule(executor1, cal.getTime());

        Thread.sleep(10);

        UserTransaction transaction = openTransaction();
        Session session = sessionFactory.getCurrentSession();

        DBEntry entry1 = (DBEntry) session.get(DBEntry.class, transactionalEntryId1);
        DBEntry entry2 = (DBEntry) session.get(DBEntry.class, transactionalEntryId2);

        assertEquals("The entry name should be " + transactionalDbEntryName, transactionalDbEntryName, entry1.getName());
        assertEquals("The entry name should be " + transactionalDbEntryName1, transactionalDbEntryName1, entry1.getName());

        commitTransaction(transaction);

        sessionFactory.close();
    }

    private class TransactionalExecutor extends TimerTask {
        private int entryId;
        private String entryName;
        private String colName;
        private SessionFactory sessionFactory;

        public TransactionalExecutor(SessionFactory sessionFactory, int entryId, String entryName, String colName) {
            this.entryId = entryId;
            this.entryName = entryName;
            this.colName = colName;
            this.sessionFactory = sessionFactory;
        }

        @Override
        public void run() {
            DBEntry entry = new DBEntry(entryName, new Date());
            entry.setId(entryId);
            DBEntryCollection collection = new DBEntryCollection(colName, entry);

            Set<DBEntryCollection> collectionSet = new HashSet<>();
            collectionSet.add(collection);

            entry.setCollection(collectionSet);
            Session session = null;
            UserTransaction tx = null;
            try {
                tx = openTransaction();
                session = sessionFactory.getCurrentSession();
                session.persist(entry);
                commitTransaction(tx);

                tx = openTransaction();
                session = sessionFactory.getCurrentSession();
                DBEntry dbEntry = (DBEntry) session.get(DBEntry.class, entryId);
                assertEquals(entryName, dbEntry.getName());
                assertEquals(entryId, dbEntry.getId());

                Set<DBEntryCollection> dbCollection = dbEntry.getCollection();
                for (Iterator<DBEntryCollection> iterator = dbCollection.iterator(); iterator.hasNext(); ) {
                    DBEntryCollection next = iterator.next();
                    assertEquals(colName, next.getName());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }*/
}
