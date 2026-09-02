package com.seuprojeto.ecommerce;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para todos os testes de integração: sobe um Postgres, um Redis e um
 * Mailpit (servidor SMTP fake, com API HTTP pra consultar os e-mails
 * recebidos) reais via Testcontainers (padrão "singleton container"),
 * compartilhados por todas as subclasses no mesmo processo de teste — evita
 * subir um container por classe e permite ao Spring reaproveitar o mesmo
 * ApplicationContext entre elas (mesmas propriedades em todas).
 */
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ecommerce_test")
                    .withUsername("test")
                    .withPassword("test");

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    protected static final GenericContainer<?> MAILPIT =
            new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
                    .withExposedPorts(1025, 8025);

    static {
        POSTGRES.start();
        REDIS.start();
        MAILPIT.start();
    }

    protected static String mailpitApiUrl() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }
}
