package org.infinispan.hibernate.test.secondLC;

import org.hibernate.SessionFactory;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cache.spi.CacheKey;
import org.hibernate.cache.spi.entry.CacheEntry;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;
import org.infinispan.hibernate.test.secondLC.data.DBEntry;
import org.infinispan.hibernate.test.secondLC.data.DBEntryCollection;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

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
    protected static final String WAR_NAME = "testable.war";

    protected static final String HIBERNATE_CFG_URL = "hibernate.cfg.xml";
    protected static final String INFINISPAN_CONFIG_NAME = "infinispan-config.xml";
    protected static final String MANIFEST_FILE_NAME = "MANIFEST.MF";
    protected static final String WEB_XML_PATH = "WEB-INF/web.xml";
    protected static final String CONTEXT_XML_PATH = "context.xml";

    protected static final String TRANSACTIONAL_WAR_NAME = "transactionalTestable.war";
    protected static final String TRANSACTIONAL_HIBERNATE_CFG_XML = "transactional/hibernate.cfg.xml";
    protected static final String TRANSACTIONAL_INFINISPAN_CONFIG_NAME = "transactional/infinispan-config.xml";

    protected static final String TESTABLE_PACKAGE = "org.infinispan.hibernate.test.secondLC";

    protected static final String LOCAL_ENTITY_CACHE_NAME = "local-entity";
    protected static final String QUERY_CACHE_NAME = "local-query";

    public static WebArchive createInfinispan2LCWebArchive(final String warName) {
        WebArchive war = ShrinkWrap.create(WebArchive.class, warName)
                .addPackages(true, new String[]{TESTABLE_PACKAGE})
                .addAsLibrary(new File("target/test-libs/mysql-connector-java-5.1.20.jar"))
                .addAsLibraries(
                        new File("target/test-libs/hibernate-core-4.1.3.Final.jar"),
                        new File("target/test-libs/hibernate-commons-annotations-4.0.1.Final.jar"),
                        new File("target/test-libs/hibernate-jpa-2.0-api-1.0.1.Final.jar"),
                        new File("target/test-libs/javassist-3.15.0-GA.jar"),
                        new File("target/test-libs/hibernate-core-4.1.3.Final.jar"),
                        new File("target/test-libs/hibernate-infinispan-4.1.3.Final.jar"));//.addAsResource("META-INF/jbossas-ds.xml");

        return war;
    }

    protected void assertStatistics(final SecondLevelCacheStatistics statistics, final int putCount, final int missCount, final int hitCount) {
        assertTrue("The hit count should be " + hitCount, statistics.getHitCount() == hitCount);
        assertTrue("The put count should be " + putCount, statistics.getPutCount() == putCount);
        assertTrue("The miss count should be " + missCount, statistics.getMissCount() == missCount);
    }

    protected EmbeddedCacheManager getCacheManager(final SessionFactory sessionFactory) {
        SessionFactoryImpl sessionFactoryImpl = (SessionFactoryImpl) sessionFactory;
        InfinispanRegionFactory regionFactory = (InfinispanRegionFactory) sessionFactoryImpl.getSettings().getRegionFactory();
        EmbeddedCacheManager cacheManager = regionFactory.getCacheManager();

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

    protected void assertQueryCacheStatistics(final Statistics stat, final int hitCount, final int putCount, final int missCount) {
        //Checking 2LC statistics
        assertEquals("The hit count should be " + hitCount, hitCount, stat.getQueryCacheHitCount());
        assertEquals("The put count should be " + putCount, putCount, stat.getQueryCachePutCount());
        assertEquals("The miss count should be " + missCount, missCount, stat.getQueryCacheMissCount());
    }
}
