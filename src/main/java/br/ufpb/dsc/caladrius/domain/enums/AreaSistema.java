package br.ufpb.dsc.caladrius.domain.enums;

import java.util.List;
import java.util.Set;

/**
 * Área do sistema tocada por um evento de auditoria — a <strong>etiqueta</strong>
 * (nome + cor) exibida na central de logs.
 *
 * <p>Enquanto {@link CategoriaAuditoria} responde "que <em>tipo</em> de evento é"
 * (acesso, operação, sistema), a área responde "<em>onde</em> no sistema mexeram":
 * viagens, veículos, WhatsApp… São eixos diferentes e complementares, e é a área que
 * a pessoa procura quando pergunta "o que andaram fazendo nas viagens ontem?".
 *
 * <p><strong>Não é uma coluna do banco</strong>: a área é <em>derivada</em> da ação e
 * da entidade já registradas. Isso vale retroativamente para toda a trilha existente
 * (nenhuma migration, nenhum backfill) e mantém o {@code log_auditoria} como o registro
 * imutável do fato — a classificação é apresentação, e pode evoluir sem reescrever o
 * passado. Se um dia a área virar filtro pesado em base grande, aí sim compensa uma
 * coluna indexada.
 */
public enum AreaSistema {

    /** Login, logout, verificação de contato e recuperação de senha. */
    ACESSO("Acesso", "acesso",
            Set.of("LOGIN_SUCESSO", "LOGIN_FALHA", "LOGOUT",
                    "LOGIN_GOOGLE_VINCULO", "LOGIN_GOOGLE_AUTOPROVISAO",
                    "CODIGO_ENVIADO", "TELEFONE_VERIFICADO", "EMAIL_VERIFICADO",
                    "SENHA_REDEFINIDA", "CONTA_ATIVADA"),
            Set.of()),

    /** Cadastro e ciclo de vida das contas (inclui convites). */
    USUARIOS("Usuários", "usuarios",
            Set.of("USUARIO_CRIADO", "USUARIO_ATUALIZADO", "USUARIO_EXCLUIDO",
                    "CONTA_SUSPENSA", "CONVITE_GERADO", "PERFIL_COMPLETADO",
                    "PASSAGEIRO_AUTOCADASTRO", "ACESSO_PLATAFORMA_TOKEN"),
            Set.of("Usuario")),

    /** Viagens e linhas programadas. */
    VIAGENS("Viagens", "viagens",
            Set.of("VIAGEM_CRIADA", "VIAGEM_DESIGNADA", "VIAGEM_EXCLUIDA", "VIAGEM_STATUS",
                    "LINHA_CRIADA", "LINHA_ATUALIZADA", "LINHA_EXCLUIDA",
                    "LINHA_HABILITADA", "LINHA_DESABILITADA"),
            Set.of("Viagem", "LinhaProgramada")),

    /** Pedidos de transporte do passageiro e a avaliação do gestor. */
    SOLICITACOES("Solicitações", "solicitacoes",
            Set.of("SOLICITACAO_CRIADA", "SOLICITACAO_CANCELADA",
                    "SOLICITACAO_APROVADA", "SOLICITACAO_RECUSADA"),
            Set.of("SolicitacaoViagem")),

    /** Frota. */
    VEICULOS("Veículos", "veiculos",
            Set.of("VEICULO_CRIADO", "VEICULO_ATUALIZADO", "VEICULO_EXCLUIDO"),
            Set.of("Veiculo")),

    /** Cidades de viagem e municípios de referência. */
    CIDADES("Cidades", "cidades",
            Set.of("CIDADE_CRIADA", "CIDADE_ATUALIZADA", "CIDADE_EXCLUIDA", "ADESAO_PAGAMENTO"),
            Set.of("Cidade", "Municipio")),

    /** Conexão da conta, configurações de envio e testes do canal. */
    WHATSAPP("WhatsApp", "whatsapp",
            Set.of("WHATSAPP_CONECTADO", "WHATSAPP_DESCONECTADO",
                    "WHATSAPP_CONFIG_ALTERADA", "WHATSAPP_TESTE_ENVIADO"),
            Set.of()),

    /** Configuração dinâmica e feature toggle (SPEC-PLT-01). */
    CONFIGURACAO("Configuração", "configuracao",
            Set.of("CONFIG_ALTERADA", "FEATURE_ALTERADA", "PARAMETRO_ALTERADO"),
            Set.of()),

    /** Tudo o que ainda não se encaixa nas áreas acima. */
    SISTEMA("Sistema", "sistema", Set.of(), Set.of());

    private final String rotulo;
    /** Sufixo da classe CSS da etiqueta (ver {@code .cal-tag--*} no caladrius.css). */
    private final String cor;
    private final Set<String> acoes;
    private final Set<String> entidades;

    AreaSistema(String rotulo, String cor, Set<String> acoes, Set<String> entidades) {
        this.rotulo = rotulo;
        this.cor = cor;
        this.acoes = acoes;
        this.entidades = entidades;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getCor() {
        return cor;
    }

    /** Ações que pertencem à área — também é o filtro usado na consulta. */
    public Set<String> getAcoes() {
        return acoes;
    }

    /**
     * Classifica um evento. A <strong>ação</strong> tem prioridade sobre a entidade
     * (é mais específica: {@code CONTA_ATIVADA} grava a entidade {@code Usuario}, mas
     * é um evento de acesso); depois a entidade; e, por fim, a categoria de segurança.
     */
    public static AreaSistema de(String acao, String entidade, CategoriaAuditoria categoria) {
        for (AreaSistema area : values()) {
            if (acao != null && area.acoes.contains(acao)) {
                return area;
            }
        }
        for (AreaSistema area : values()) {
            if (entidade != null && area.entidades.contains(entidade)) {
                return area;
            }
        }
        return categoria == CategoriaAuditoria.SEGURANCA ? ACESSO : SISTEMA;
    }

    /** Todas as ações classificadas — o complemento identifica a área SISTEMA. */
    public static List<String> todasAsAcoesConhecidas() {
        return List.of(values()).stream().flatMap(a -> a.acoes.stream()).toList();
    }
}
