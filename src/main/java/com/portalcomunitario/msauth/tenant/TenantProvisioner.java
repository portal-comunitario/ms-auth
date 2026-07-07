package com.portalcomunitario.msauth.tenant;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Provisiona el schema de un tenant en ms-auth: migraciones de auth + creación del admin de la comunidad. */
@Service
public class TenantProvisioner {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioner.class);

    private final DataSourceConfig dataSourceConfig;
    private final TenantRoutingDataSource routingDataSource;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("${spring.datasource.password}")
    private String password;

    public TenantProvisioner(DataSourceConfig dataSourceConfig,
                             TenantRoutingDataSource routingDataSource,
                             PasswordEncoder passwordEncoder) {
        this.dataSourceConfig = dataSourceConfig;
        this.routingDataSource = routingDataSource;
        this.passwordEncoder = passwordEncoder;
    }

    public synchronized void provision(String schema) {
        Flyway.configure()
                .dataSource(url, username, password)
                .schemas(schema)
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .locations("classpath:db/migration")
                .load()
                .migrate();
        routingDataSource.addDataSource(schema, dataSourceConfig.createDataSource(schema));
        log.info("Tenant '{}' provisionado (auth)", schema);
    }

    /** Crea (idempotente) el administrador de la comunidad en el schema del tenant. */
    public void crearAdmin(String schema, String email, String nombre, String password) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSourceConfig.createDataSource(schema));
        String correo = email != null ? email.trim().toLowerCase() : "";
        Integer existe = jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, correo);
        if (existe != null && existe > 0) {
            log.info("Admin '{}' ya existe en '{}', se omite", correo, schema);
            return;
        }
        String hash = passwordEncoder.encode(password != null ? password : "");
        jdbc.update(
                "INSERT INTO users (email, name, password_hash, role, notificaciones_activas, acceso_aprobado, estado_validacion) "
                        + "VALUES (?, ?, ?, 'COMMUNITY_ADMIN', false, true, 'PENDIENTE')",
                correo, nombre != null ? nombre : "Administrador", hash);
        log.info("Admin '{}' creado en '{}'", correo, schema);
    }
}
