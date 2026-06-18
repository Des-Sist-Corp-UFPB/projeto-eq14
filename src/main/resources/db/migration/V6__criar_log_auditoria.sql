-- ===========================================================================
--  Migração V6 — Trilha de auditoria (log_auditoria)
-- ---------------------------------------------------------------------------
--  Registra "quem fez o quê, quando". Duas visões sobre a mesma tabela:
--    - SYSADMIN : trilha completa (SEGURANCA + OPERACAO + SISTEMA).
--    - GERENTE  : histórico de OPERAÇÃO (criações/edições/exclusões de negócio).
--
--  usuario_id/usuario_nome são DENORMALIZADOS (sem FK): a trilha sobrevive à
--  exclusão do usuário. Migration aditiva e forward-only (Constituição Art. IV).
-- ===========================================================================

CREATE TABLE log_auditoria (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    instante     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    categoria    VARCHAR(20)  NOT NULL
                 CHECK (categoria IN ('SEGURANCA', 'OPERACAO', 'SISTEMA')),
    acao         VARCHAR(60)  NOT NULL,
    resultado    VARCHAR(20)  NOT NULL DEFAULT 'SUCESSO'
                 CHECK (resultado IN ('SUCESSO', 'FALHA')),
    usuario_id   UUID,
    usuario_nome VARCHAR(180),
    entidade     VARCHAR(60),
    entidade_id  VARCHAR(80),
    detalhe      VARCHAR(500),
    ip           VARCHAR(60)
);

CREATE INDEX idx_log_auditoria_instante  ON log_auditoria (instante DESC);
CREATE INDEX idx_log_auditoria_categoria ON log_auditoria (categoria);

COMMENT ON TABLE log_auditoria IS
    'Trilha de auditoria (acessos e operações). Sysadmin vê tudo; gerente vê OPERACAO.';
