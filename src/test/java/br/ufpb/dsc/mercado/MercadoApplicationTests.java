package br.ufpb.dsc.mercado;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de carregamento do contexto Spring Boot.
 *
 * <p><strong>O que este teste verifica?</strong><br>
 * {@code @SpringBootTest} inicializa o contexto completo do Spring Boot e verifica se
 * todos os beans são criados corretamente, sem erros de configuração ou dependências
 * circulares. É o teste mais básico e deve sempre passar.
 *
 * <p><strong>{@code @ActiveProfiles("test")}:</strong><br>
 * Ativa o perfil "test", que usa as configurações de {@code application-test.yml}.
 * Isso garante que os testes usem um banco de dados isolado (Testcontainers)
 * em vez do banco de desenvolvimento.
 *
 * <p><strong>Testcontainers:</strong><br>
 * Para que este teste funcione, o Docker deve estar rodando.
 * O Testcontainers sobe automaticamente um container PostgreSQL para os testes
 * e o derruba ao final. Isso garante que os testes de integração usem um banco
 * real, idêntico ao de produção, sem depender de configurações locais.
 *
 * @author DSC - UFPB Campus IV
 */
@SpringBootTest
@Testcontainers // Ativa o gerenciamento automático dos containers pelo JUnit 5
@ActiveProfiles("test")
class MercadoApplicationTests {

    /**
     * Container PostgreSQL gerenciado pelo Testcontainers.
     *
     * <p>{@code @SpringBootTest} carrega o contexto completo, o que inicializa o
     * DataSource, o JPA e o Flyway — todos precisam de um banco real. Sem este
     * container (com {@code @ServiceConnection} configurando a URL automaticamente),
     * o contexto não sobe e o teste falha com "Failed to load ApplicationContext".
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Verifica que o contexto Spring Boot carrega sem erros.
     *
     * <p>Se este teste falhar, significa que há algum problema de configuração:
     * bean faltando, propriedade incorreta, dependência circular, etc.
     * É o primeiro teste a executar e o mais importante para detectar problemas de setup.
     */
    @Test
    void contextLoads() {
        // Se chegar aqui sem lançar exceção, o contexto carregou com sucesso
    }
}
