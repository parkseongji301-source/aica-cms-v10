package egovframework.backoffice.mvp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import egovframework.backoffice.mvp.operations.FileDatabaseSafety;

/** Memory fixtures migrate; every file runtime only validates. CutoverTool is the sole file migrator. */
@Configuration(proxyBeanMethods=false)
public class ClassificationMigrationConfiguration {
    @Bean
    FlywayMigrationStrategy classificationMigrationStrategy(DataSourceProperties datasource,Environment environment,
            @Value("${backoffice.classification-migration.copy-validation:false}") boolean copyValidation) {
        return flyway -> {
            if(datasource.getUrl()!=null && datasource.getUrl().startsWith("jdbc:h2:mem:")) {
                requireValidationDatabase(datasource.getUrl(),environment.acceptsProfiles(Profiles.of("local")),copyValidation);
                flyway.migrate();
            } else {
                flyway.validate();
                FileDatabaseSafety.require(flyway.info().current()!=null && "10".equals(flyway.info().current().getVersion().getVersion()) && flyway.info().pending().length==0,
                    "normal file runtime must already be V10; migration is disabled");
            }
        };
    }

    public static void requireValidationDatabase(String url,boolean localProfile,boolean copyValidation) {
        boolean originalPath=url!=null && url.replace('\\','/').replace(':','/').toLowerCase(java.util.Locale.ROOT).contains("/.local-data/");
        if(localProfile || originalPath || url==null || (!url.startsWith("jdbc:h2:mem:") && !copyValidation))
            throw new IllegalStateException("3B-2A: original DB migration is disabled. Use the preserved 3A runtime for local; "
                +"validate a copied DB with the dev profile and backoffice.classification-migration.copy-validation=true.");
    }
}
