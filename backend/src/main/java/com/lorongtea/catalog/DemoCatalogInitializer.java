package com.lorongtea.catalog;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
class DemoCatalogInitializer implements ApplicationRunner {
    private final DataSource dataSource;

    DemoCatalogInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator seed =
                new ResourceDatabasePopulator(new ClassPathResource("db/demo/catalog.sql"));
        seed.setSqlScriptEncoding("UTF-8");
        seed.execute(dataSource);
    }
}
