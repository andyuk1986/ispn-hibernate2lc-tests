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
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;
import java.util.*;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ISPNSecondLevelInvalidationTest extends AbstractISPNSecondLevelCacheTest {

    private int elementId = 700;
    private String elementName = "test700";
    private String elementColName = "testCol700";

    private int newCreatedElem = 10001;
    private String newCreatedElemName ="newElement";

    @PersistenceContext
    EntityManager manager;

    @Inject
    UserTransaction tx;

    @Deployment(name = "node1")
    @TargetsContainer("container1")
    public static WebArchive createNode1Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(INVALIDATION_CONFIG_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(INVALIDATION_CONFIG_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Deployment(name = "node2")
    @TargetsContainer("container2")
    public static WebArchive createNode2Deployment() {
        WebArchive jar = createInfinispan2LCWebArchive(NODE1_WAR_NAME);
        jar.addAsResource(INVALIDATION_CONFIG_PERSISTENCE_URL, PERSISTENCE_URL)
                .addAsResource(INVALIDATION_CONFIG_INFINISPAN_CONFIG_NAME, INFINISPAN_CONFIG_NAME)
                .addAsResource(JGROUPS_CONFIG_NAME)
                .addAsManifestResource(MANIFEST_FILE_NAME);

        System.out.println(jar.toString(true));

        return jar;
    }

    @Test
    @InSequence(1)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForInvalidationInsertNode1() throws Exception {
        EmbeddedCacheManager cacheManager = prepareCache(manager, ENTITY_CACHE_NAME);

        DBEntry entry = manager.find(DBEntry.class, elementId);

        assertNotNull(entry);
        assertEquals("The entry name should be " + elementName, elementName, entry.getName());

        Set<DBEntryCollection> collection = entry.getCollection();
        for (Iterator<DBEntryCollection> iterator = collection.iterator(); iterator.hasNext(); ) {
            DBEntryCollection next = iterator.next();

            assertEquals("The entry collection name should be " + elementColName, elementColName, next.getName());
        }

        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        entry = new DBEntry(elementName, new Date());
        DBEntryCollection collection1 = new DBEntryCollection(elementColName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection1);
        entry.setCollection(set);
        expectedElems.put(elementId, entry);

        DBEntry newEntry = new DBEntry(newCreatedElemName, new Date());
        tx.begin();
        manager.joinTransaction();
        manager.persist(newEntry);
        tx.commit();

        expectedElems.put(newCreatedElem, newEntry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 3, expectedElems);
    }

    @Test
    @InSequence(2)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForInvalidationInsertNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<Integer, DBEntry> expectedElems = new HashMap<Integer, DBEntry>();
        DBEntry entry = new DBEntry(elementName, new Date());
        DBEntryCollection collection = new DBEntryCollection(elementColName, entry);

        Set<DBEntryCollection> set = new HashSet<DBEntryCollection>();
        set.add(collection);
        entry.setCollection(set);
        expectedElems.put(elementId, entry);

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 0, null);

        entry = manager.find(DBEntry.class, elementId);

        DBEntry newEntry = manager.find(DBEntry.class, newCreatedElem);

        assertNotNull(entry);
        assertEquals("The entry name should be  " + elementName, elementName, entry.getName());

        assertNotNull(newEntry);
        assertEquals("The entry name should be  " + newCreatedElemName, newCreatedElemName, newEntry.getName());

        expectedElems.put(newCreatedElem, newEntry);
        assertCacheManagerStatistics(cachemap, 3, expectedElems);
    }

    @Test
    @InSequence(3)
    @OperateOnDeployment("node1")
    public void testSecondLevelCacheForInvalidationDeleteNode1() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());

        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);
        assertCacheManagerStatistics(cachemap, 3, null);

        tx.begin();
        DBEntry entry = manager.find(DBEntry.class, newCreatedElem);
        manager.remove(entry);

        tx.commit();

        assertCacheManagerStatistics(cachemap, 2, null);
    }

    @Test
    @InSequence(4)
    @OperateOnDeployment("node2")
    public void testSecondLevelCacheForInvalidationDeleteNode2() throws Exception {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> cachemap = cacheManager.getCache(ENTITY_CACHE_NAME);

        assertCacheManagerStatistics(cachemap, 2, null);
    }
}
