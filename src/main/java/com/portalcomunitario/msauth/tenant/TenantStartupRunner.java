package com.portalcomunitario.msauth.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** Al arrancar, provisiona los schemas de las comunidades ya registradas (sobrevive reinicios). */
@Component
public class TenantStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantStartupRunner.class);

    private final DataSourceConfig dataSourceConfig;
    private final TenantProvisioner provisioner;

    public TenantStartupRunner(DataSourceConfig dataSourceConfig, TenantProvisioner provisioner) {
        this.dataSourceConfig = dataSourceConfig;
        this.provisioner = provisioner;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSourceConfig.createDataSource("public"));
            List<String> slugs = jdbc.queryForList("SELECT slug FROM comunidad", String.class);
            for (String slug : slugs) {
                try {
                    provisioner.provision(slug);
                } catch (Exception e) {
                    log.error("No se pudo provisionar el tenant '{}' al arrancar: {}", slug, e.getMessage());
                }
            }
            log.info("Provisionados {} tenant(s) al arrancar (auth)", slugs.size());
        } catch (Exception e) {
            log.warn("No se pudo leer el registro de comunidades al arrancar (¿ms-tenant aún no lo creó?): {}", e.getMessage());
        }
    }
}
