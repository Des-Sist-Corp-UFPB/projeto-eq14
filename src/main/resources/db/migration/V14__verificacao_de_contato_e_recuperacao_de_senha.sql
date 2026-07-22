-- V14 — Verificação de contato e recuperação de senha (SPEC-12 / ADR-16)
-- Evolução ADITIVA (Art. IV): não altera migrations existentes, sem extensões
-- nem superusuário (Art. XIV). Enums como VARCHAR + CHECK (Art. VI); UUID nativo.

-- Estado de verificação do contato (ortogonal ao status — como perfil_incompleto).
ALTER TABLE usuarios
    ADD COLUMN telefone_verificado_em TIMESTAMPTZ,   -- NULL = telefone não verificado
    ADD COLUMN email_verificado_em    TIMESTAMPTZ;   -- NULL = e-mail não verificado

-- O link mágico ganha finalidade; o DEFAULT preserva a semântica atual do
-- convite/ativação (ADR-11). Usado pela verificação de e-mail (etapa seguinte).
ALTER TABLE tokens_ativacao
    ADD COLUMN finalidade VARCHAR(30) NOT NULL DEFAULT 'ATIVACAO'
        CHECK (finalidade IN ('ATIVACAO', 'VERIFICAR_EMAIL'));

-- Código OTP (6 dígitos): guarda só o HASH, com expiração, uso único e lockout.
CREATE TABLE codigos_verificacao (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario      UUID        NOT NULL REFERENCES usuarios (id),
    codigo_hash  VARCHAR(120) NOT NULL,
    finalidade   VARCHAR(30) NOT NULL
                 CHECK (finalidade IN ('VERIFICAR_TELEFONE', 'RESET_SENHA')),
    canal        VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP'
                 CHECK (canal IN ('WHATSAPP', 'EMAIL')),
    tentativas   INTEGER     NOT NULL DEFAULT 0,
    criado_em    TIMESTAMPTZ NOT NULL,
    expira_em    TIMESTAMPTZ NOT NULL,
    usado_em     TIMESTAMPTZ,
    criado_ip    VARCHAR(45)
);

CREATE INDEX ix_codigos_usuario_finalidade
    ON codigos_verificacao (usuario, finalidade);
