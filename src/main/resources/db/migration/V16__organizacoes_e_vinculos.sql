-- V16 — Organizações e vínculos (SPEC-PLT-02 / ADR-21 e ADR-22), fase 1.
--
-- Plano de CONTROLE do modelo multi-tenant: quem são as secretarias (organizacoes) e
-- quem pertence a cada uma, com qual papel (vinculos). Migration ADITIVA (Art. IV):
-- nenhuma tabela existente é alterada, nada é apagado e o deploy atual continua
-- funcionando exatamente como hoje — sem vínculo, a aplicação opera no "tenant legado".
--
-- O que esta migration NÃO faz (fase 2, ver SPEC-PLT-02 §11):
--   - não cria schema de tenant nem move tabela operacional;
--   - não divide `usuarios` em identidade + membro;
--   - não substitui `papeis_usuario`, que segue sendo a fonte dos papéis hoje.

CREATE TABLE organizacoes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome         VARCHAR(160) NOT NULL,
    slug         VARCHAR(60)  NOT NULL,
    documento    VARCHAR(18),                      -- CNPJ; opcional no rascunho
    status       VARCHAR(30)  NOT NULL DEFAULT 'RASCUNHO'
                 CHECK (status IN ('RASCUNHO', 'AGUARDANDO_PAGAMENTO', 'ATIVA',
                                   'INADIMPLENTE', 'SUSPENSA', 'CANCELADA')),
    -- Schema de dados do tenant (RN-MT-14). NULO = organização ainda servida pelo
    -- schema legado; a fase 2 preenche ao provisionar. Guardado aqui para que o
    -- provisionamento não precise de outra migration.
    schema_dados VARCHAR(63),
    criado_em    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- O slug identifica o tenant (subdomínio e, na fase 2, nome do schema): não pode colidir.
CREATE UNIQUE INDEX ux_organizacoes_slug ON organizacoes (slug);

COMMENT ON TABLE organizacoes IS
    'Secretaria cliente (SPEC-PLT-02): fronteira de isolamento do modelo multi-tenant.';
COMMENT ON COLUMN organizacoes.schema_dados IS
    'Schema de dados do tenant (fase 2). NULO = servida pelo schema legado.';

-- Vínculo: a evolução de papeis_usuario com a dimensão ORGANIZAÇÃO (ADR-22).
-- O papel deixa de valer globalmente e passa a valer dentro de uma secretaria.
CREATE TABLE vinculos (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario      UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    organizacao  UUID        NOT NULL REFERENCES organizacoes (id),
    papel        VARCHAR(30) NOT NULL
                 CHECK (papel IN ('PASSAGEIRO', 'MOTORISTA', 'GERENTE', 'SYSADMIN')),
    -- RN-MT-08: nasce PENDENTE — ninguém entra em uma secretaria por conta própria.
    status       VARCHAR(30) NOT NULL DEFAULT 'PENDENTE'
                 CHECK (status IN ('PENDENTE', 'ATIVO', 'REVOGADO')),
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    aprovado_em  TIMESTAMPTZ,
    aprovado_por UUID        REFERENCES usuarios (id)
);

-- Um vínculo vivo por (pessoa, secretaria, papel). Revogados não contam — a pessoa
-- pode ser readmitida sem apagar o histórico (RN-MT-16), como no soft-delete (Art. III).
CREATE UNIQUE INDEX ux_vinculos_pessoa_org_papel
    ON vinculos (usuario, organizacao, papel)
    WHERE status <> 'REVOGADO';

-- Leitura mais frequente do sistema: "quais os vínculos de quem acabou de logar?"
CREATE INDEX ix_vinculos_usuario ON vinculos (usuario);

COMMENT ON TABLE vinculos IS
    'Pertencimento pessoa × secretaria × papel (SPEC-PLT-02 ADR-22). Sucede papeis_usuario.';
