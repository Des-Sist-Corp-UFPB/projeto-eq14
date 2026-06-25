-- ===========================================================================
--  Migracao V11 - Solicitacoes de viagem do passageiro (SPEC-09)
-- ---------------------------------------------------------------------------
--  Funcao-fim do passageiro: ele ve as linhas disponiveis e SOLICITA transporte
--  numa data. O gerente, ao designar a linha naquela data (materializar a
--  viagem - SPEC-06), aloca o passageiro: a solicitacao passa a apontar para a
--  viagem e o passageiro ve os detalhes (motorista, veiculo, horario, destino).
--
--  Escopo simplificado (decisao do dono do projeto): o carro busca o passageiro
--  no seu proprio local, entao NAO ha local de partida na solicitacao - apenas
--  a linha (destino + horarios) e a data desejada.
--
--  Observacao: as tabelas placeholder solicitacoes_transporte/assentos_viagem
--  (criadas especulativamente na V2, nunca mapeadas) NAO sao usadas por esta
--  feature; seguem intactas (migration aditiva, forward-only - Constituicao
--  Art. IV) e podem ser removidas num incremento futuro de limpeza.
-- ===========================================================================

CREATE TABLE solicitacoes_viagem (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    passageiro       UUID        NOT NULL REFERENCES usuarios (id),
    linha_programada UUID        NOT NULL REFERENCES linhas_programadas (id) ON DELETE CASCADE,
    data_desejada    DATE        NOT NULL,
    -- Preenchida quando a viagem da linha+data e designada (alocacao). ON DELETE
    -- SET NULL: se o gerente excluir a viagem, a solicitacao volta a ficar sem
    -- alocacao em vez de quebrar por FK.
    viagem           UUID        REFERENCES viagens (id) ON DELETE SET NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
                     CHECK (status IN ('PENDENTE', 'ALOCADA', 'CANCELADA', 'RECUSADA')),
    observacao       VARCHAR(280),
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Listagem do passageiro (aba "Minhas viagens"), mais recentes primeiro.
CREATE INDEX ix_solicitacoes_viagem_passageiro ON solicitacoes_viagem (passageiro, criado_em DESC);

-- Evita solicitacao duplicada do mesmo passageiro para a mesma linha+data,
-- ignorando as canceladas (que podem ser re-solicitadas).
CREATE UNIQUE INDEX ux_solicitacao_viagem_unica
    ON solicitacoes_viagem (passageiro, linha_programada, data_desejada)
    WHERE status <> 'CANCELADA';

COMMENT ON TABLE solicitacoes_viagem IS 'Solicitacoes de transporte do passageiro numa linha+data (SPEC-09)';
