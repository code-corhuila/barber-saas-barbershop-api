package co.edu.corhuila.barbersaas.barbershop.app;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.AuthFilter;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Rs256Verifier;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.InMemoryBarberRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.InMemoryBarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.InMemoryServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.JdbcBarberRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.JdbcBarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.JdbcServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.UuidGenerator;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ServiceUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.application.usecase.ManageBarbers;
import co.edu.corhuila.barbersaas.barbershop.application.usecase.ManageBarbershops;
import co.edu.corhuila.barbersaas.barbershop.application.usecase.ManageServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composition root: the only place that knows every concrete type. The pool and its limits are
 * built here explicitly (norm 5.3.10), so they are read in code review instead of hidden in defaults.
 */
@Configuration
public class BarbershopConfiguration {

    /** The JDBC access, or none when DATABASE_URL is empty (in-memory repositories, no database needed). */
    record Database(JdbcTemplate jdbc, TransactionTemplate tx) {
        Optional<Database> present() {
            return jdbc == null ? Optional.empty() : Optional.of(this);
        }
    }

    @Bean
    Database database(@Value("${barbershop.database.url:}") String url,
                      @Value("${barbershop.database.user:}") String user,
                      @Value("${barbershop.database.password:}") String password,
                      @Value("${barbershop.database.pool-max:10}") int poolMax,
                      @Value("${barbershop.database.statement-timeout-ms:5000}") int statementTimeoutMs) {
        if (url.isBlank()) {
            return new Database(null, null);
        }
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setUsername(user);                                       // barbershop_app, never the administrator
        pool.setPassword(password);
        pool.setMaximumPoolSize(poolMax);
        pool.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
        pool.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        pool.setConnectionInitSql("SET statement_timeout = " + statementTimeoutMs);
        HikariDataSource dataSource = new HikariDataSource(pool);
        return new Database(new JdbcTemplate(dataSource),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Bean
    BarbershopRepository barbershopRepository(Database database) {
        return database.present().<BarbershopRepository>map(d -> new JdbcBarbershopRepository(d.jdbc()))
                .orElseGet(InMemoryBarbershopRepository::new);
    }

    @Bean
    ServiceRepository serviceRepository(Database database) {
        return database.present().<ServiceRepository>map(d -> new JdbcServiceRepository(d.jdbc(), d.tx()))
                .orElseGet(InMemoryServiceRepository::new);
    }

    @Bean
    BarberRepository barberRepository(Database database) {
        return database.present().<BarberRepository>map(d -> new JdbcBarberRepository(d.jdbc(), d.tx()))
                .orElseGet(InMemoryBarberRepository::new);
    }

    @Bean
    BarbershopUseCases barbershopUseCases(BarbershopRepository barbershops, ServiceRepository services,
                                          BarberRepository barbers) {
        return new ManageBarbershops(barbershops, services, barbers, Clock.systemUTC());
    }

    @Bean
    ServiceUseCases serviceUseCases(ServiceRepository services) {
        return new ManageServices(services, new UuidGenerator(), Clock.systemUTC());
    }

    @Bean
    BarberUseCases barberUseCases(BarberRepository barbers) {
        return new ManageBarbers(barbers, new UuidGenerator());
    }

    /** JWT_PUBLIC_KEY: the PEM itself; a one-line value with literal \n escapes, as an env file holds it, is accepted. */
    @Bean
    Rs256Verifier tokenVerifier(@Value("${JWT_PUBLIC_KEY:}") String pem) {
        return new Rs256Verifier(pem.replace("\\n", "\n"));
    }

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilter() {
        FilterRegistrationBean<CorrelationFilter> bean = new FilterRegistrationBean<>(new CorrelationFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(Rs256Verifier verifier, ObjectMapper json) {
        FilterRegistrationBean<AuthFilter> bean = new FilterRegistrationBean<>(new AuthFilter(verifier, json));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
