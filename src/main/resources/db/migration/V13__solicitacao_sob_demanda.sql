-- ===========================================================================
--  Migracao V13 - Solicitacao de transporte SOB DEMANDA + onboarding (SPEC-11)
-- ---------------------------------------------------------------------------
--  Realiza a RN-SOL-08 (adiada na SPEC-09): o passageiro pede transporte SEM
--  depender de uma linha pre-programada - informa destino + data + horario +
--  condicoes (comorbidade/deficiencia) e o gestor avalia, aprova (designando
--  uma viagem imprevista) ou recusa.
--
--  Decisao D1 (ADR-15): estender a tabela existente `solicitacoes_viagem` em
--  vez de criar outra (fila unica para o gestor). Um `tipo` distingue os dois
--  casos; `linha_programada` deixa de ser obrigatoria (a demanda nao tem linha).
--
--  Tambem amplia `conversas_bot` (SPEC-10) com o contexto dos novos fluxos do
--  bot (cadastro + pedido sob demanda) - colunas tipadas, sem JSONB (ADR-13).
--
--  Migration aditiva, forward-only (Art. IV); sem extensoes (Art. XIV).
-- ===========================================================================

-- ─────────────────────── solicitacoes_viagem: sob demanda ───────────────────

-- A demanda nao tem linha: a coluna deixa de ser NOT NULL.
ALTER TABLE solicitacoes_viagem ALTER COLUMN linha_programada DROP NOT NULL;

ALTER TABLE solicitacoes_viagem
    ADD COLUMN tipo             VARCHAR(20) NOT NULL DEFAULT 'POR_LINHA'
                                CHECK (tipo IN ('POR_LINHA', 'SOB_DEMANDA')),
    -- Destino da demanda (cidade cadastrada). CASCADE: se a cidade for removida
    -- (remocao fisica - Art. III), as solicitacoes de/para ela vao junto, sem
    -- deixar a coluna nula (o CHECK abaixo exige destino quando SOB_DEMANDA).
    ADD COLUMN cidade_destino   UUID REFERENCES cidades (id) ON DELETE CASCADE,
    ADD COLUMN horario_desejado TIME,
    -- Comorbidade/deficiencia informada pelo passageiro (avaliar prioridade).
    ADD COLUMN condicoes        VARCHAR(280),
    -- Preenchido ao RECUSAR (o status RECUSADA ja existia no CHECK da V11).
    ADD COLUMN motivo_recusa    VARCHAR(280);

-- Integridade por tipo: linha para POR_LINHA; destino para SOB_DEMANDA.
ALTER TABLE solicitacoes_viagem
    ADD CONSTRAINT ck_solicitacao_tipo_coerente CHECK (
        (tipo = 'POR_LINHA'   AND linha_programada IS NOT NULL) OR
        (tipo = 'SOB_DEMANDA' AND cidade_destino   IS NOT NULL)
    );

-- Sem duplicata de demanda ativa para o mesmo destino+data (analogo a RN-SOL-04).
CREATE UNIQUE INDEX ux_solicitacao_demanda_unica
    ON solicitacoes_viagem (passageiro, cidade_destino, data_desejada)
    WHERE tipo = 'SOB_DEMANDA' AND status <> 'CANCELADA';

COMMENT ON COLUMN solicitacoes_viagem.tipo IS 'POR_LINHA (SPEC-09) ou SOB_DEMANDA (SPEC-11)';

-- ─────────────────────── conversas_bot: contexto dos novos fluxos ───────────

ALTER TABLE conversas_bot
    -- Contexto do pedido sob demanda (espelha as colunas da solicitacao).
    ADD COLUMN cidade_destino   UUID REFERENCES cidades (id) ON DELETE SET NULL,
    ADD COLUMN horario_desejado TIME,
    ADD COLUMN condicoes        VARCHAR(280),
    -- Rascunho do cadastro (onboarding) enquanto o bot coleta os dados minimos.
    ADD COLUMN cad_nome         VARCHAR(160),
    ADD COLUMN cad_endereco     VARCHAR(280),
    ADD COLUMN cad_cpf          VARCHAR(14);

-- Amplia o CHECK de `etapa` para as novas etapas do bot (onboarding + demanda).
-- O CHECK inline da V12 recebe o nome padrao do Postgres (conversas_bot_etapa_check).
ALTER TABLE conversas_bot DROP CONSTRAINT IF EXISTS conversas_bot_etapa_check;
ALTER TABLE conversas_bot
    ADD CONSTRAINT conversas_bot_etapa_check CHECK (etapa IN (
        'INICIO', 'MENU', 'ESCOLHER_LINHA', 'ESCOLHER_DATA', 'CONFIRMAR',
        'CANCELAR', 'HUMANO', 'ENCERRADA',
        -- SPEC-11:
        'ONBOARDING_NOME', 'ONBOARDING_ENDERECO', 'ONBOARDING_CPF',
        'ESCOLHER_DESTINO', 'ESCOLHER_HORARIO', 'CONDICOES', 'CONFIRMAR_DEMANDA'
    ));
