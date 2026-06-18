-- ===========================================================================
--  Migração V5 — Configurações do sistema (chave/valor)
-- ---------------------------------------------------------------------------
--  Parâmetros de runtime geridos pelo SYSADMIN (sem redeploy). Modelo
--  chave/valor simples e flexível. Usado por:
--    - sessao.timeout_minutos : tempo de inatividade da sessão (DT-10), aplicado
--      via HttpSessionListener (config DINÂMICA — lida do banco, não do YAML).
--    - geral.cidade_sede_id   : cidade-sede (origem padrão das viagens, SPEC-06).
--
--  Migration aditiva e forward-only (Constituição Art. IV).
-- ===========================================================================

CREATE TABLE configuracoes_sistema (
    chave         VARCHAR(80)  PRIMARY KEY,
    valor         VARCHAR(255) NOT NULL,
    atualizado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE configuracoes_sistema IS
    'Parâmetros de runtime geridos pelo SYSADMIN (chave/valor)';

-- Valor padrão do tempo de sessão (30 minutos, igual ao default do Tomcat).
INSERT INTO configuracoes_sistema (chave, valor) VALUES ('sessao.timeout_minutos', '30');
