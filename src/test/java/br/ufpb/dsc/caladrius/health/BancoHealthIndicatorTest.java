package br.ufpb.dsc.caladrius.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários de {@link BancoHealthIndicator} — healthcheck de banco (SPEC-OPE-02 §9).
 *
 * <p>Cobrem o <strong>caminho de falha</strong> e a <strong>propagação do timeout até o
 * JDBC</strong> — justamente o que não dá para observar contra um banco saudável. O caminho
 * feliz é exercido de ponta a ponta, contra um PostgreSQL real, em
 * {@code web.PaginasPublicasTest}: lá o {@code /ping} devolve {@code database=up} e o
 * {@code /actuator/health} exibe o componente {@code banco}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BancoHealthIndicator — Healthcheck de banco (SPEC-OPE-02)")
class BancoHealthIndicatorTest {

    @Mock private DataSource dataSource;

    /**
     * Banco inacessível ⇒ DOWN, sem propagar exceção. Falhar ao obter a conexão é o modo
     * de falha real em produção (banco compartilhado fora do ar, rede, pool esgotado); o
     * {@code JdbcTemplate} traduz a {@link SQLException} em {@code DataAccessException},
     * que o indicador captura.
     */
    @Test
    @DisplayName("banco inacessível → DOWN (sem propagar exceção)")
    void bancoInacessivel_ficaDown() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        BancoHealthIndicator indicador = new BancoHealthIndicator(dataSource, 2);

        assertThat(indicador.acessivel()).isFalse();
        assertThat(indicador.health().getStatus()).isEqualTo(Status.DOWN);
    }

    /**
     * RN-HC-04: o detalhe publicado não pode conter a mensagem do driver — ela carrega host,
     * base e usuário do PostgreSQL, e o {@code /actuator/health} é rota pública.
     */
    @Test
    @DisplayName("DOWN não expõe a mensagem do driver nos detalhes")
    void down_naoExpoeMensagemDoDriver() throws SQLException {
        when(dataSource.getConnection())
                .thenThrow(new SQLException("FATAL: password authentication failed for user \"eq14\""));

        Health saude = new BancoHealthIndicator(dataSource, 2).health();

        assertThat(saude.getDetails()).containsOnlyKeys("consulta", "timeoutSegundos");
        assertThat(saude.getDetails().toString()).doesNotContain("password", "eq14");
    }

    /**
     * RN-HC-02: o teto configurado chega mesmo ao JDBC. Sem isso, um banco pendurado
     * penduraria junto o {@code /ping} e o healthcheck do container.
     */
    @Test
    @DisplayName("aplica o timeout configurado na consulta (RN-HC-02)")
    void aplicaTimeoutNaConsulta() throws SQLException {
        Connection conexao = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.createStatement()).thenReturn(statement);
        when(statement.executeQuery(BancoHealthIndicator.CONSULTA))
                .thenThrow(new SQLException("canceling statement due to statement timeout"));

        boolean acessivel = new BancoHealthIndicator(dataSource, 7).acessivel();

        verify(statement).setQueryTimeout(7);
        assertThat(acessivel).isFalse();
    }

    /**
     * A consulta é constante e independente do schema: contar linhas de uma tabela do
     * domínio acoplaria a saúde do sistema ao modelo de dados e quebraria a cada migration.
     */
    @Test
    @DisplayName("a consulta é independente do schema")
    void consultaIndependeDoSchema() {
        assertThat(BancoHealthIndicator.CONSULTA).isEqualTo("SELECT 1");
    }
}
