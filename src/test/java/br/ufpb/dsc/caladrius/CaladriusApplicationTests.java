package br.ufpb.dsc.caladrius;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de carregamento do contexto.
 *
 * <p>Sobe o contexto completo do Spring Boot contra um PostgreSQL real
 * (Testcontainers). Isso exercita, de uma só vez:
 * <ul>
 *   <li>a aplicação das migrações Flyway (V1, V2, V3);</li>
 *   <li>a validação do schema pelo Hibernate ({@code ddl-auto: validate}) — ou
 *       seja, garante que as entidades batem com as tabelas;</li>
 *   <li>a criação do administrador pelo {@code DataInitializer}.</li>
 * </ul>
 *
 * <p>Requer Docker em execução.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CaladriusApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Se o contexto subir sem exceção, migrações + validação + seed funcionaram.
    }
}
