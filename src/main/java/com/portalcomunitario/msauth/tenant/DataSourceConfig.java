package com.portalcomunitario.msauth.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean
    public TenantRoutingDataSource dataSource() {
        TenantRoutingDataSource routingDataSource = new TenantRoutingDataSource();

        DriverManagerDataSource defaultDs = createDataSource("public");
        routingDataSource.setDefaultTargetDataSource(defaultDs);
        routingDataSource.addDataSource("public", defaultDs);

        return routingDataSource;
    }

    public DriverManagerDataSource createDataSource(String schema) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(url + "?currentSchema=" + schema);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }
}
