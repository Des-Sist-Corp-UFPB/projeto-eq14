-- ===========================================================================
--  Migração V4 — Papel SYSADMIN (administrador do sistema)
-- ---------------------------------------------------------------------------
--  Adiciona o papel SYSADMIN ao RBAC. É um papel ISOLADO (least privilege):
--  cuida da configuração do sistema (sessão, logs/auditoria, parâmetros) sob
--  /admin/**, separado da operação do GERENTE. Quem precisar dos dois mundos
--  acumula os dois papéis (a tabela papeis_usuario é N:N).
--
--  Migration aditiva e forward-only (Constituição Art. IV; Plano §2.5):
--  recria apenas o CHECK da coluna `papel`. O nome `papeis_usuario_papel_check`
--  é o gerado automaticamente pelo PostgreSQL para o CHECK anônimo da V2.
-- ===========================================================================

ALTER TABLE papeis_usuario DROP CONSTRAINT papeis_usuario_papel_check;

ALTER TABLE papeis_usuario
    ADD CONSTRAINT papeis_usuario_papel_check
    CHECK (papel IN ('PASSAGEIRO', 'MOTORISTA', 'GERENTE', 'SYSADMIN'));

COMMENT ON TABLE papeis_usuario IS
    'Papéis (RBAC) de cada usuário: PASSAGEIRO, MOTORISTA, GERENTE, SYSADMIN';
