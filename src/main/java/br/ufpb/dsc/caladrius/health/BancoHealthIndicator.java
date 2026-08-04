package br.ufpb.dsc.caladrius.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Health check do <strong>banco de dados</strong> (SPEC-OPE-02) — verificação
 * <em>explícita</em> de que o PostgreSQL responde a uma consulta.
 *
 * <p><strong>Por que esta classe existe</strong>, se o Spring Boot já traz o
 * {@code DataSourceHealthIndicator}: o requisito da disciplina é um healthcheck que
 * <em>consulta o banco</em> e que seja <strong>legível no código do projeto</strong>
 * (RN-HC-01). O indicador embutido faz a consulta dentro do framework — não há nada
 * no nosso repositório que comprove a verificação. Aqui a consulta está à vista:
 * {@link #CONSULTA}, executada por {@link JdbcTemplate}, com timeout.
 *
 * <p>Entra automaticamente no agregado {@code /actuator/health} como o componente
 * {@code banco} (o Spring deriva a chave do nome do bean) e é consumido também pelo
 * {@code PingController}, que reporta o estado no contrato público {@code /ping} —
 * <strong>sempre 200</strong>, nunca 503 (RN-HC-03).
 *
 * <p>O {@link JdbcTemplate} é <strong>próprio</strong>, construído a partir do
 * {@link DataSource}, e não o bean compartilhado da aplicação: {@code queryTimeout} é
 * estado do template, e alterá-lo no bean global imporia o timeout do healthcheck a
 * todas as consultas do sistema.
 */
@Component
public class BancoHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(BancoHealthIndicator.class);

    /**
     * Consulta mínima do healthcheck. É deliberadamente independente do schema: contar
     * linhas de uma tabela do domínio acoplaria a saúde do sistema ao modelo de dados e
     * quebraria a cada migration que renomeasse algo.
     */
    static final String CONSULTA = "SELECT 1";

    private final JdbcTemplate jdbc;
    private final int timeoutSegundos;

    public BancoHealthIndicator(
            DataSource dataSource,
            @Value("${caladrius.health.banco.timeout-segundos:2}") int timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
        this.jdbc = new JdbcTemplate(dataSource);
        // Sem timeout, um banco pendurado pendura junto o /ping e o healthcheck do
        // container — pior que um DOWN honesto e rápido (RN-HC-02).
        this.jdbc.setQueryTimeout(timeoutSegundos);
    }

    /**
     * Executa a consulta e diz se o banco respondeu.
     *
     * <p>É o único ponto que fala com o banco: {@link #health()} e o {@code /ping}
     * derivam daqui, para não existirem duas verificações que possam divergir.
     *
     * @return {@code true} se o banco respondeu dentro do timeout
     */
    public boolean acessivel() {
        try {
            jdbc.queryForObject(CONSULTA, Integer.class);
            return true;
        } catch (DataAccessException e) {
            // Só a classe da exceção: a mensagem do driver pode conter host, base e
            // usuário, e este detalhe chega ao /actuator/health (RN-HC-04).
            log.warn("Healthcheck do banco falhou ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Health health() {
        Health.Builder resultado = acessivel() ? Health.up() : Health.down();
        return resultado
                .withDetail("consulta", CONSULTA)
                .withDetail("timeoutSegundos", timeoutSegundos)
                .build();
    }
}
