package com.hazelcast.table.impl;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.internal.services.ManagedService;
import com.hazelcast.internal.services.RemoteService;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.spi.impl.operationservice.PartitionAwareOperation;
import com.hazelcast.table.TableProxy;

import java.util.Properties;
import java.util.UUID;

public class TableService implements PartitionAwareOperation, ManagedService, RemoteService {

    public static final String SERVICE_NAME = "hz:impl:tableService";
    private final NodeEngineImpl nodeEngine;

    public TableService(NodeEngineImpl nodeEngine) {
        this.nodeEngine = nodeEngine;
    }

    @Override
    public void init(NodeEngine nodeEngine, Properties properties) {

    }

    @Override
    public DistributedObject createDistributedObject(String objectName, UUID source, boolean local) {
        return new TableProxy(nodeEngine, this, objectName);
    }

    @Override
    public void destroyDistributedObject(String objectName, boolean local) {
        throw new RuntimeException();
    }

    @Override
    public DistributedObject createDistributedObject(String objectName, UUID source) {
        throw new RuntimeException();
    }

    @Override
    public void destroyDistributedObject(String objectName) {
        throw new RuntimeException();
    }

    @Override
    public void reset() {

    }

    @Override
    public void shutdown(boolean terminate) {

    }

    @Override
    public int getPartitionId() {
        return 0;
    }
}