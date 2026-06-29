package com.portalcomunitario.msauth.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }

    public synchronized void addDataSource(String tenantId, DataSource dataSource) {
        targetDataSources.put(tenantId, dataSource);
        super.setTargetDataSources(new HashMap<>(targetDataSources));
        super.afterPropertiesSet();
    }

    public boolean hasTenant(String tenantId) {
        return targetDataSources.containsKey(tenantId);
    }
}
