package br.ufpb.dsc.caladrius.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da classificação de eventos em {@link AreaSistema} — a etiqueta exibida
 * na central de logs ({@code /logs}).
 *
 * <p>A área é <strong>derivada</strong> da ação/entidade já gravadas, sem coluna nova.
 * Estes cenários travam as duas decisões que importam: a <em>ordem</em> de
 * classificação (ação antes de entidade) e o fato de nada ficar sem etiqueta.
 */
@DisplayName("AreaSistema — etiquetas da central de logs")
class AreaSistemaTest {

    @Test
    @DisplayName("classifica pela ação, que é o sinal mais específico")
    void classificaPelaAcao() {
        assertThat(AreaSistema.de("VIAGEM_CRIADA", null, CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.VIAGENS);
        assertThat(AreaSistema.de("LINHA_EXCLUIDA", null, CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.VIAGENS);
        assertThat(AreaSistema.de("WHATSAPP_DESCONECTADO", null, CategoriaAuditoria.SISTEMA))
                .isEqualTo(AreaSistema.WHATSAPP);
        assertThat(AreaSistema.de("FEATURE_ALTERADA", null, CategoriaAuditoria.SISTEMA))
                .isEqualTo(AreaSistema.CONFIGURACAO);
        assertThat(AreaSistema.de("SOLICITACAO_APROVADA", "SolicitacaoViagem", CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.SOLICITACOES);
    }

    @Test
    @DisplayName("a ação vence a entidade: CONTA_ATIVADA grava 'Usuario' mas é evento de acesso")
    void acaoVenceEntidade() {
        assertThat(AreaSistema.de("CONTA_ATIVADA", "Usuario", CategoriaAuditoria.SEGURANCA))
                .isEqualTo(AreaSistema.ACESSO);
        // já um CRUD comum da mesma entidade continua em Usuários
        assertThat(AreaSistema.de("USUARIO_ATUALIZADO", "Usuario", CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.USUARIOS);
    }

    @Test
    @DisplayName("sem ação conhecida, cai na entidade")
    void caiNaEntidade() {
        assertThat(AreaSistema.de("ACAO_QUE_NAO_EXISTE", "Veiculo", CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.VEICULOS);
        assertThat(AreaSistema.de("ACAO_QUE_NAO_EXISTE", "Municipio", CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.CIDADES);
    }

    @Test
    @DisplayName("evento de segurança desconhecido ainda assim é Acesso (não fica órfão)")
    void segurancaDesconhecidaViraAcesso() {
        assertThat(AreaSistema.de("ALGO_NOVO_DE_LOGIN", null, CategoriaAuditoria.SEGURANCA))
                .isEqualTo(AreaSistema.ACESSO);
    }

    @Test
    @DisplayName("o que não se encaixa em nada vira Sistema — nenhum log fica sem etiqueta")
    void desconhecidoViraSistema() {
        assertThat(AreaSistema.de("ACAO_FUTURA", null, CategoriaAuditoria.SISTEMA))
                .isEqualTo(AreaSistema.SISTEMA);
        assertThat(AreaSistema.de(null, null, CategoriaAuditoria.OPERACAO))
                .isEqualTo(AreaSistema.SISTEMA);
    }

    @Test
    @DisplayName("toda área tem rótulo e cor, e nenhuma ação está em duas áreas")
    void catalogoConsistente() {
        for (AreaSistema area : AreaSistema.values()) {
            assertThat(area.getRotulo()).isNotBlank();
            assertThat(area.getCor()).isNotBlank();
        }
        assertThat(AreaSistema.todasAsAcoesConhecidas())
                .doesNotHaveDuplicates()
                .isNotEmpty();
    }
}
