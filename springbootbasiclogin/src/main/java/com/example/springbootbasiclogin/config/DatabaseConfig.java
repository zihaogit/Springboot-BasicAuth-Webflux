package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.util.ZonedDateTimeConverterUtil;
import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DatabaseConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(ApplicationPropertiesConfig applicationProperties) {
        ApplicationPropertiesConfig.FlywayProperties flywayProperties = applicationProperties.getSpring().getFlyway();

        return Flyway.configure()
                .dataSource(
                        flywayProperties.getUrl(),
                        flywayProperties.getUsername(),
                        flywayProperties.getPassword()
                )
                .locations(flywayProperties.getLocations().toArray(String[]::new))
                .baselineOnMigrate(flywayProperties.getBaselineOnMigrate())
                .load();
    }

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory, ZoneId zoneId) {
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);
        List<Object> converters = new ArrayList<>(dialect.getConverters());
        converters.addAll(ZonedDateTimeConverterUtil.getConvertersToRegister(zoneId));
        return R2dbcCustomConversions.of(dialect, converters);
    }
}
