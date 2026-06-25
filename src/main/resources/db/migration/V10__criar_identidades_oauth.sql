-- ===========================================================================
--  Migracao V10 - Identidades externas (OAuth2/OIDC) e perfil incompleto (SPEC-08)
-- ---------------------------------------------------------------------------
--  - identidades_oauth: vincula um Usuario a uma identidade de provedor externo
--    (ex.: Google). Chave de identidade = (provedor, subject_id), onde subject_id
--    e o 'sub' IMUTAVEL do provedor (o e-mail pode mudar; o sub nao).
--  - usuarios.perfil_incompleto: marcador (ortogonal ao status) de conta criada
--    por login social que ainda nao informou os dados obrigatorios (telefone).
--  - usuarios.telefone passa a aceitar NULL: a conta auto-provisionada nasce sem
--    telefone (o Google nao o fornece) e o completa em /conta/completar. Os fluxos
--    normais (cadastro/criacao pelo gerente) continuam exigindo telefone no servico.
--
--  Migration aditiva e forward-only (Constituicao Art. IV). UUID nativo, enum como
--  VARCHAR + CHECK, sem extensoes/superusuario (banco compartilhado).
-- ===========================================================================

CREATE TABLE identidades_oauth (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario         UUID         NOT NULL REFERENCES usuarios(id),
    provedor        VARCHAR(20)  NOT NULL,
    subject_id      VARCHAR(255) NOT NULL,
    email_provedor  VARCHAR(160),
    vinculado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_identidades_provedor CHECK (provedor IN ('GOOGLE')),
    CONSTRAINT uq_identidades_provedor_subject UNIQUE (provedor, subject_id)
);

CREATE INDEX ix_identidades_usuario ON identidades_oauth(usuario);

COMMENT ON TABLE identidades_oauth IS 'Vinculo entre Usuario e identidade de provedor externo (OAuth2/OIDC) - SPEC-08';

-- Marcador de perfil incompleto (Opcao A) - ortogonal ao status.
ALTER TABLE usuarios ADD COLUMN perfil_incompleto BOOLEAN NOT NULL DEFAULT FALSE;

-- Telefone passa a aceitar NULL (ver cabecalho).
ALTER TABLE usuarios ALTER COLUMN telefone DROP NOT NULL;
