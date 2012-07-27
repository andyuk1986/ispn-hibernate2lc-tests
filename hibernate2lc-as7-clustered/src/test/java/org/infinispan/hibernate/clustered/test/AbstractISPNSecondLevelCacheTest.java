package org.infinispan.hibernate.clustered.test;

import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.ejb.EntityManagerFactoryImpl;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.infinispan.hibernate.clustered.test.data.DBEntry;
import org.infinispan.hibernate.clustered.test.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 *
 */
@RunWith(Arquillian.class)
public abstract class AbstractISPNSecondLevelCacheTest {
    protected static final String PERSISTENCE_UNIT_NAME = "hib2Lc";
    protected static final String NODE1_WAR_NAME = "testable.war";
    protected static final String NODE2_WAR_NAME = "testable2.war";

    protected static final String PERSISTENCE_URL = "META-INF/persistence.xml";
    protected static final String INFINISPAN_CONFIG_NAME = "infinispan-config.xml";
    protected static final String JGROUPS_CONFIG_NAME = "jgroups-tcp.xml";
    protected static final String MANIFEST_FILE_NAME = "MANIFEST.MF";

    protected static final String TRANSACTIONAL_WAR_NAME = "transactionalTestable.war";
    protected static final String TRANSACTIONAL_PERSISTENCE_URL = "transactional/META-INF/persistence.xml";
    protected static final String TRANSACTIONAL_INFINISPAN_CONFIG_NAME = "transactional/infinispan-config.xml";

    protected static final String QUERY_CACHE_NAME = "testable.war#hib2Lc.queryCache";
    protected static final String ENTITY_CACHE_NAME = "testable.war#hib2Lc.entityCache";
    protected static final String COLLECTION_CACHE_NAME = "testable.war#hib2Lc.collectionCache";

    protected static final String TESTABLE_PACKAGE = "org.infinispan.hibernate.clustered.test";

    public static WebArchive createInfinispan2LCWebArchive(final String warName) {
        WebArchive war = ShrinkWrap.create(WebArchive.class, warName)
                .addPackages(true, new String[] {TESTABLE_PACKAGE})
                .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
                .addAsLibraries(
                        new File("target/test-libs/hibernate-core-4.1.3.Final.jar"),
                        new File("target/test-libs/hibernate-commons-annotations-4.0.1.Final.jar"),
                        new File("target/test-libs/hibernate-jpa-2.0-api-1.0.1.Final.jar"),
                        new File("target/test-libs/javassist-3.15.0-GA.jar"),
                        new File("target/test-libs/jboss-annotations-ejb3-4.2.2.GA.jar"),
                        new File("target/test-libs/hibernate-infinispan-4.1.3.Final.jar"));//.addAsResource("META-INF/jbossas-ds.xml");

        return war;
    }

    protected EmbeddedCacheManager getCacheManager(final EntityManagerFactory emf) {
        EntityManagerFactoryImpl entityManagerFactoryImpl = (EntityManagerFactoryImpl) emf;
        InfinispanRegionFactory factory = (InfinispanRegionFactory) entityManagerFactoryImpl.getSessionFactory().getSettings().getRegionFactory();

        EmbeddedCacheManager cacheManager = factory.getCacheManager();

        return cacheManager;
    }

    protected void assertCacheManagerStatistics(final Map<CacheKey, CacheEntry> cache, final int size, Map<Integer, DBEntry> expectedValues) {
        assertEquals("The cache size should be " + size, size, cache.size());
        if(size > 0 && expectedValues != null) {
            for(Map.Entry<CacheKey, CacheEntry> cacheEntry : cache.entrySet()) {
                CacheKey key = cacheEntry.getKey();
                DBEntry expectedValue = expectedValues.get(key.getKey());
                if(key.getEntityOrRoleName().equals(DBEntry.class.getCanonicalName()) && expectedValue != null) {
                    assertEquals(expectedValue.getName(), cacheEntry.getValue().getDisassembledState()[2]);
                } else if (key.getEntityOrRoleName().equals(DBEntryCollection.class.getCanonicalName())) {
                    Set<DBEntryCollection> col = expectedValue.getCollection();
                    assertTrue(!col.isEmpty());

                    Iterator<DBEntryCollection> iterator = col.iterator();
                    if (iterator.hasNext()) {
                        DBEntryCollection next = iterator.next();

                        assertEquals(next.getName(), cacheEntry.getValue().getDisassembledState()[1]);
                    }
                }
            }
        }
    }

    protected EmbeddedCacheManager prepareCache(final EntityManager manager, final String cacheName) {
        EmbeddedCacheManager cacheManager = getCacheManager(manager.getEntityManagerFactory());
        Map<CacheKey, CacheEntry> cacheElems = cacheManager.getCache(cacheName);
        //Clearing cache
        cacheElems.clear();
        //checking that the cache is empty
        assertCacheManagerStatistics(cacheElems, 0, null);

        return cacheManager;
    }
}
