package com.integ.integration.product.connectivity.xads;

import com.hazelcast.core.TransactionalMap;
import com.integ.integration.product.connectivity.hazelcast.XAHazelcastManager;
import com.integ.integration.product.connectivity.hazelcast.impl.XAHazelcastManagerImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.transaction.*;
import java.sql.SQLException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
        "classpath:/private-tests/test-context.xml",
        "classpath:/private-tests/connection-hazelcast-test.xml"

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TransactionConfiguration
public class XAHazelcastManagerTest extends Assert {

    @Autowired
    private TransactionManager transactionManager;

    @Autowired
    private XAHazelcastManager xaHazelcastManager;

    static {
        System.setProperty("hazelcast1.port", "32345");
    }


    @Test(expected = IllegalStateException.class)
    @Transactional(propagation = Propagation.NEVER)
    public void xaHazelcastManagerTest_no_transaction() throws SQLException {
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
    }

    @Test
    public void xaHazelcastManagerTest_in_transaction() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        testMap.put("A", 1);

        transactionManager.commit();
    }

    @Test
    public void xaHazelcastManagerTestSynchronisation_delisted_commit() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");

        assertEquals(1, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Enlisted during TX

        transactionManager.commit();

        assertEquals(0, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Delixted automatically after
    }

    @Test
    public void xaHazelcastManagerTestMultipleGet_should_have_one_in_context() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");

        assertEquals(1, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Enlisted during TX

        transactionManager.commit();

        assertEquals(0, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Delixted automatically after
    }

    @Test
    public void xaHazelcastManagerTestSynchronisation_delisted_rollback() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        xaHazelcastManager.getContextForCurrentTransaction().getMap("test");

        assertEquals(1, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Enlisted during TX

        transactionManager.rollback();

        assertEquals(0, ((XAHazelcastManagerImpl)xaHazelcastManager).getContextSize()); //Delixted automatically after
    }

    @Test
    public void xaHazelcastManagerTestSynchronisation_empty_map_after_rollback() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        testMap.put("A", 1);
        assertNotNull(testMap.get("A"));

        transactionManager.rollback();

        transactionManager.begin();

        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        assertNull(testMap.get("A"));

        transactionManager.rollback();
    }

    @Test
    public void xaHazelcastManagerTestSynchronisation_not_empty_map_after_commit() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        transactionManager.begin();

        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        testMap.put("A", 1);

        transactionManager.commit();

        transactionManager.begin();

        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("test");
        assertNotNull(testMap.get("A"));

        transactionManager.rollback();
    }

    @Test
    public void xaHazelcastTestMapWithIndexes_fails_on_index() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException, InterruptedException {
        transactionManager.begin();
        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("stringMap");
        testMap.put("A", 1);
        assertNotNull(testMap.get("A"));
        transactionManager.commit();

        transactionManager.begin();
        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("idle10s");
        assertNull(testMap.get("A"));
        transactionManager.rollback();
    }

    @Test
    public void xaHazelcastTestMapWithIndexes_added_successfully() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException, InterruptedException {
        transactionManager.begin();
        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("stringMap");
        testMap.put("A", "1");
        assertNotNull(testMap.get("A"));
        transactionManager.commit();

        transactionManager.begin();
        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("stringMap");
        assertNotNull(testMap.get("A"));
        transactionManager.rollback();
    }

    @Test
    public void xaHazelcastTestConfigurationWithEviction_entry_evicted_after_10s_idle_tiem() throws SQLException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException, InterruptedException {
        transactionManager.begin();
        TransactionalMap<Object, Object> testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("idle10s");
        testMap.put("A", 1);
        assertNotNull(testMap.get("A"));
        transactionManager.commit();

        transactionManager.begin();
        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("idle10s");
        assertNotNull(testMap.get("A"));
        transactionManager.rollback();

        Thread.sleep(15 * 1000);

        transactionManager.begin();
        testMap = xaHazelcastManager.getContextForCurrentTransaction().getMap("idle10s");
        assertNull(testMap.get("A"));
        transactionManager.rollback();
    }
}
