package com.integ.integration.product.connectivity.hazelcast.impl;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.HazelcastXAResource;
import com.hazelcast.transaction.TransactionContext;
import com.integ.integration.product.connectivity.hazelcast.XAHazelcastManager;
import org.apache.geronimo.transaction.manager.NamedXAResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.*;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.HashMap;
import java.util.Map;

/**
 *
 *
 * Manages Hazelcast's transactional contexts, auto-enlisting them in current transaction, also ensuring only
 * one context to be enlisted in one transaction. For that purpose it keeps tracking of created contexts per
 * transaction.
 * To not run OOM, it also registers synchronisation, to detach context from transaction on commit/rollback.
 *
 */
public class XAHazelcastManagerImpl implements XAHazelcastManager {
    private static final Logger LOG = LoggerFactory.getLogger(XAHazelcastManagerImpl.class);

    @Autowired
    private HazelcastInstance hazelcastInstance;

    private Map<Transaction, TransactionContext> contextMap = new HashMap<>();

    @Autowired
    private TransactionManager transactionManager;

    @Override
    public TransactionContext getContextForCurrentTransaction() {
        try {
            final Transaction transaction = transactionManager.getTransaction();

            if (transaction == null) {
                throw new IllegalStateException("No current transaction found!");
            }

            if (contextMap.containsKey(transaction)) { // quick check without synchronisation
                LOG.debug("Found Hazelcast Transactional Context, for transaction: " + transaction);

                return contextMap.get(transaction);
            }

            synchronized (this) {
                if (contextMap.containsKey(transaction)) { // slow recheck with synchronisation to avoid double enlistment and synchronisation
                    return contextMap.get(transaction);
                }

                LOG.debug("Creating new Hazelcast Transactional Context and enlisting it in transaction: " + transaction);

                NamedHazelcastXAResource xaResource = new NamedHazelcastXAResource(hazelcastInstance.getXAResource());
                transaction.enlistResource(xaResource);
                transaction.registerSynchronization(new Synchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        LOG.debug("Removing Hazelcast Transactional Context from contextMap for transaction: " + transaction);
                        TransactionContext removed = XAHazelcastManagerImpl.this.contextMap.remove(transaction);
                        if (removed == null) {
                            LOG.error("Current hazelcast context was not found!");
                        }
                    }

                    @Override
                    public void beforeCompletion() {
                        //no-op
                    }
                });

                TransactionContext transactionContext = xaResource.getTransactionContext();
                contextMap.put(transaction, transactionContext);

                return transactionContext;
            }
        } catch (RollbackException | SystemException ex) {
            throw new RuntimeException(ex);
        }
    }

    public int getContextSize() {
        return contextMap.size();
    }

    public class NamedHazelcastXAResource implements NamedXAResource, HazelcastXAResource {
        private final HazelcastXAResource hazelcastXAResource;

        public NamedHazelcastXAResource(HazelcastXAResource hazelcastXAResource) {
            this.hazelcastXAResource = hazelcastXAResource;
        }

        @Override
        public TransactionContext getTransactionContext() {
            return hazelcastXAResource.getTransactionContext();
        }

        @Override
        public String getPartitionKey() {
            return hazelcastXAResource.getPartitionKey();
        }

        @Override
        public String getServiceName() {
            return hazelcastXAResource.getServiceName();
        }

        @Override
        public void destroy() {
            hazelcastXAResource.destroy();
        }

        @Override
        public String getName() {
            return hazelcastXAResource.getName();
        }

        @Override
        public void commit(Xid xid, boolean b) throws XAException {
            hazelcastXAResource.commit(xid, b);
        }

        @Override
        public void end(Xid xid, int i) throws XAException {
            hazelcastXAResource.end(xid, i);
        }

        @Override
        public void forget(Xid xid) throws XAException {
            hazelcastXAResource.forget(xid);
        }

        @Override
        public int getTransactionTimeout() throws XAException {
            return hazelcastXAResource.getTransactionTimeout();
        }

        @Override
        public boolean isSameRM(XAResource xaResource) throws XAException {
            return hazelcastXAResource.isSameRM(xaResource);
        }

        @Override
        public int prepare(Xid xid) throws XAException {
            return hazelcastXAResource.prepare(xid);
        }

        @Override
        public Xid[] recover(int i) throws XAException {
            return hazelcastXAResource.recover(i);
        }

        @Override
        public void rollback(Xid xid) throws XAException {
            hazelcastXAResource.rollback(xid);
        }

        @Override
        public boolean setTransactionTimeout(int i) throws XAException {
            return hazelcastXAResource.setTransactionTimeout(i);
        }

        @Override
        public void start(Xid xid, int i) throws XAException {
            hazelcastXAResource.start(xid, i);
        }
    }

}
