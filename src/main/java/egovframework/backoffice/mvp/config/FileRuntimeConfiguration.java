package egovframework.backoffice.mvp.config;

import com.zaxxer.hikari.HikariDataSource;
import egovframework.backoffice.mvp.operations.FileDatabaseSafety;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.slf4j.LoggerFactory;
import static egovframework.backoffice.mvp.operations.FileDatabaseSafety.*;

/** Bind all Hikari overrides, then check the actual URL before the pool can open a connection. */
@Configuration(proxyBeanMethods=false)
public class FileRuntimeConfiguration {
    @Bean(destroyMethod="close")
    public HikariDataSource dataSource(DataSourceProperties properties,Environment env) throws Exception {
        HikariDataSource ds=properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        try {
            Binder.get(env).bind("spring.datasource.hikari",Bindable.ofInstance(ds));
            require(properties.getUrl()!=null&&properties.getUrl().equals(ds.getJdbcUrl()),"datasource/Hikari URL mismatch");
            require(env.getProperty("spring.flyway.url")==null,"separate Flyway datasource is not supported");
            require(ds.getDataSourceClassName()==null&&ds.getDataSource()==null&&ds.getDataSourceProperties().isEmpty(),"alternate Hikari connection properties are not supported");
            if(ds.getJdbcUrl().startsWith("jdbc:h2:mem:"))return ds;
            require("org.h2.Driver".equals(ds.getDriverClassName()),"unexpected file JDBC driver");
            String init=ds.getConnectionInitSql();
            require(init==null || init.equals("SET TIME ZONE '"+env.getProperty("backoffice.time-zone","Asia/Seoul")+"'"),"unapproved connection init SQL");
            require(!"always".equals(env.getProperty("spring.sql.init.mode")),"file SQL initialization is disabled");
            require(FileDatabaseSafety.LOCATION.equals(env.getProperty("spring.flyway.locations",FileDatabaseSafety.LOCATION)),"unapproved Flyway migration location");
            require(!env.getProperty("AICA_CUTOVER_ENABLED","false").equalsIgnoreCase("true"),"cutover mode must not be used by the web server");
            // Every promotion tool so far (V11 up to the current schema) has its own flag; none may reach the web server.
            for(int version=11;version<=Integer.parseInt(CURRENT_VERSION);version++)
                require(!env.getProperty("AICA_V"+version+"_PROMOTION_ENABLED","false").equalsIgnoreCase("true"),"promotion mode must not be used by the web server");
            Path db=requireWriter(ds.getJdbcUrl());
            boolean original=db.toString().replace('\\','/').toLowerCase(Locale.ROOT).contains("/.local-data/");
            String receipt=env.getProperty("AICA_RUNTIME_RECEIPT","");
            if(original || !env.getProperty("backoffice.classification-migration.copy-validation",Boolean.class,false)) {
                require(!receipt.isBlank(),"original DB is blocked without a completed cutover receipt");
                requireReceipt(db,Path.of(receipt),hash(runtimeJar()),CURRENT_VERSION);
            }
            requireCurrentSchema(db,ds.getUsername(),ds.getPassword());
            LoggerFactory.getLogger(FileRuntimeConfiguration.class).info("V"+CURRENT_VERSION+" file runtime: profile={}, path={}, AUTO_COMPACT_FILL_RATE=0, migration=validate-only, pid={}",
                String.join(",",env.getActiveProfiles()),db,ProcessHandle.current().pid());
            return ds;
        } catch(Exception e) {ds.close();throw e;}
    }
}
