-- ===========================================================================
--  Migração V7 — Tokens de ativação (convites) e notificações in-app
-- ---------------------------------------------------------------------------
--  Onboarding por convite (#20):
--    - SYSADMIN convida GERENTE; GERENTE cria MOTORISTA — em ambos os casos o
--      usuário nasce PENDENTE (sem senha utilizável) e recebe um TOKEN de ativação.
--    - O convidado define a senha via token → conta ATIVA.
--
--  Segurança: guarda-se apenas o HASH do token (nunca o valor cru), com
--  expiração e uso único. Notificações in-app alimentam o sino do topo.
--
--  Migration aditiva e forward-only (Constituição Art. IV).
-- ===========================================================================

CREATE TABLE tokens_ativacao (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(120) NOT NULL UNIQUE,
    usuario    UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    criado_por UUID         REFERENCES usuarios (id),
    criado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expira_em  TIMESTAMPTZ  NOT NULL,
    usado_em   TIMESTAMPTZ
);

CREATE INDEX idx_tokens_ativacao_usuario ON tokens_ativacao (usuario);

CREATE TABLE notificacoes (
    id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario   UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    titulo    VARCHAR(160) NOT NULL,
    mensagem  VARCHAR(500),
    lida      BOOLEAN      NOT NULL DEFAULT false,
    criado_em TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notificacoes_usuario ON notificacoes (usuario, lida);

COMMENT ON TABLE tokens_ativacao IS 'Tokens de ativação de conta (convites). Guarda apenas o hash.';
COMMENT ON TABLE notificacoes  IS 'Notificações in-app exibidas no sino do topo.';
