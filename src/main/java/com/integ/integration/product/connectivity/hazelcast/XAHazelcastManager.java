package com.integ.integration.product.connectivity.hazelcast;

import com.hazelcast.transaction.TransactionContext;

public interface XAHazelcastManager {
    TransactionContext getContextForCurrentTransaction();
}
