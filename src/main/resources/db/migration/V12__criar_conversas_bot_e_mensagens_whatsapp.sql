-- ===========================================================================
--  Migracao V12 - Integracao WhatsApp (SPEC-10)
-- ---------------------------------------------------------------------------
--  Duas tabelas para tirar o canal WhatsApp do estado de stub:
--
--  * conversas_bot: estado da maquina de estados do bot de atendimento (uma
--    conversa por numero). O contexto do fluxo de solicitacao (linha escolhida,
--    data desejada) fica em COLUNAS TIPADAS - sem JSONB, coerente com a decisao
--    da V8/ADR-13. Persistir o estado permite que a conversa sobreviva a
--    redeploys da aplicacao.
--
--  * mensagens_whatsapp: log de toda mensagem enviada/recebida (RN-WPP-10) -
--    alimenta o painel /whatsapp do gerente e garante a idempotencia do
--    webhook (eventos repetidos da Evolution sao ignorados pelo id_provedor).
--
--  Migration aditiva, forward-only (Constituicao Art. IV); sem extensoes nem
--  superusuario (Art. XIV - banco compartilhado, plano tecnico secao 2.5).
-- ===========================================================================

CREATE TABLE conversas_bot (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Telefone normalizado (apenas digitos, sem DDI 55) - casa com usuarios.telefone.
    telefone         VARCHAR(20) NOT NULL,
    -- Resolvido na identificacao (RN-WPP-05); pode ser NULL para numero desconhecido.
    usuario          UUID        REFERENCES usuarios (id),
    etapa            VARCHAR(20) NOT NULL DEFAULT 'INICIO'
                     CHECK (etapa IN ('INICIO', 'MENU', 'ESCOLHER_LINHA', 'ESCOLHER_DATA',
                                      'CONFIRMAR', 'CANCELAR', 'HUMANO', 'ENCERRADA')),
    -- Contexto do fluxo de solicitacao em andamento (limpo ao concluir/expirar).
    linha_programada UUID        REFERENCES linhas_programadas (id) ON DELETE SET NULL,
    data_desejada    DATE,
    -- Base da expiracao por inatividade (RN-WPP-07) e da pausa em HUMANO (RN-WPP-08).
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Uma unica conversa por numero de telefone.
CREATE UNIQUE INDEX ux_conversa_bot_telefone ON conversas_bot (telefone);

CREATE TABLE mensagens_whatsapp (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    direcao     VARCHAR(10)   NOT NULL CHECK (direcao IN ('RECEBIDA', 'ENVIADA')),
    telefone    VARCHAR(20)   NOT NULL,
    -- Conteudo truncado em 1000 caracteres pela aplicacao (RN-WPP-10).
    conteudo    VARCHAR(1000) NOT NULL,
    -- Id da mensagem no provedor (Evolution) - so as recebidas o possuem.
    id_provedor VARCHAR(80),
    criado_em   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Painel do gerente: ultimas mensagens primeiro.
CREATE INDEX ix_mensagens_whatsapp_criado ON mensagens_whatsapp (criado_em DESC);

-- Idempotencia do webhook: evento repetido (mesmo id no provedor) nao e reprocessado.
CREATE UNIQUE INDEX ux_mensagem_whatsapp_provedor
    ON mensagens_whatsapp (id_provedor)
    WHERE direcao = 'RECEBIDA';

COMMENT ON TABLE conversas_bot IS 'Estado da maquina de estados do bot de atendimento WhatsApp (SPEC-10)';
COMMENT ON TABLE mensagens_whatsapp IS 'Log de mensagens WhatsApp enviadas/recebidas - painel e idempotencia (SPEC-10)';
