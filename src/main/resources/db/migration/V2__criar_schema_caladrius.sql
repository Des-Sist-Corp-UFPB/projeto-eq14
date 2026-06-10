-- ============================================================================
--  Migração V2 — Schema do domínio CALADRIUS
-- ----------------------------------------------------------------------------
--  A migração V1 (tabela "produto" do boilerplate) é mantida intacta — NUNCA
--  edite uma migração já aplicada (o Flyway compara checksums). Esta V2 cria
--  todo o schema do CALADRIUS. A V3 remove a tabela "produto" e insere dados
--  iniciais.
--
--  Decisões de modelagem:
--   - PK: UUID com DEFAULT gen_random_uuid(). Em PostgreSQL 13+ essa função é
--     NATIVA (pg_catalog) — não exige a extensão pgcrypto, evitando problemas
--     de permissão no banco compartilhado da disciplina.
--   - Enums: armazenados como VARCHAR (os valores são os nomes das enums Java,
--     em MAIÚSCULAS), com CHECK nas tabelas mapeadas pela aplicação. Essa opção
--     é a mais robusta com o Hibernate (ddl-auto: validate) — evita o atrito de
--     mapear enums nativas do PostgreSQL.
--   - Datas/horas: DATE/TIME; timestamps: TIMESTAMPTZ.
--   - Soft-delete: coluna removido_em em usuarios e veiculos; unicidade via
--     índices únicos PARCIAIS (apenas entre registros ativos).
-- ============================================================================

-- ─────────────────────────────── Usuários ──────────────────────────────────
CREATE TABLE usuarios (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome_completo VARCHAR(160)             NOT NULL,
    cpf           VARCHAR(11),
    email         VARCHAR(160),
    telefone      VARCHAR(20)              NOT NULL,
    hash_senha    VARCHAR(100),
    status        VARCHAR(30)              NOT NULL DEFAULT 'ATIVO'
                  CHECK (status IN ('PENDENTE', 'ATIVO', 'INATIVO', 'SUSPENSO')),
    criado_em     TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    removido_em   TIMESTAMPTZ
);

-- Unicidade apenas entre usuários ATIVOS (soft-delete) e ignorando nulos.
CREATE UNIQUE INDEX ux_usuarios_telefone ON usuarios (telefone) WHERE removido_em IS NULL;
CREATE UNIQUE INDEX ux_usuarios_cpf      ON usuarios (cpf)      WHERE removido_em IS NULL AND cpf   IS NOT NULL;
CREATE UNIQUE INDEX ux_usuarios_email    ON usuarios (lower(email)) WHERE removido_em IS NULL AND email IS NOT NULL;

-- Papéis (RBAC) — N papéis por usuário.
CREATE TABLE papeis_usuario (
    usuario       UUID        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    papel         VARCHAR(30) NOT NULL
                  CHECK (papel IN ('PASSAGEIRO', 'MOTORISTA', 'GERENTE')),
    concedido_em  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    concedido_por UUID REFERENCES usuarios (id),
    PRIMARY KEY (usuario, papel)
);

-- ─────────────────────────────── Perfis ────────────────────────────────────
-- (Tabelas do domínio; ainda não mapeadas por entidades — uso em incremento futuro.)
CREATE TABLE perfis_passageiro (
    usuario             UUID PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    data_nascimento     DATE,
    endereco            JSONB,
    condicao_saude      TEXT,
    mobilidade_reduzida BOOLEAN NOT NULL DEFAULT FALSE,
    cadeirante          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE perfis_motorista (
    usuario       UUID PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    numero_cnh    VARCHAR(20) NOT NULL,
    categoria_cnh VARCHAR(5)  NOT NULL,
    validade_cnh  DATE        NOT NULL,
    matricula     VARCHAR(40) NOT NULL,
    data_admissao DATE        NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'ATIVO'
);

CREATE TABLE perfis_gerente (
    usuario   UUID PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
    matricula VARCHAR(40) NOT NULL,
    cargo     VARCHAR(80) NOT NULL
);

-- ─────────────────────────────── Cidades ───────────────────────────────────
CREATE TABLE cidades (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(120) NOT NULL,
    uf   VARCHAR(2)   NOT NULL,
    tipo VARCHAR(30)  NOT NULL
         CHECK (tipo IN ('ORIGEM', 'METROPOLITANA'))
);

-- ─────────────────────────────── Veículos ──────────────────────────────────
CREATE TABLE veiculos (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    placa                 VARCHAR(8)  NOT NULL,
    marca                 VARCHAR(60) NOT NULL,
    modelo                VARCHAR(60) NOT NULL,
    ano                   INTEGER     NOT NULL,
    tipo                  VARCHAR(30) NOT NULL
                          CHECK (tipo IN ('CARRO', 'VAN', 'MICRO_ONIBUS', 'ONIBUS', 'AMBULANCIA')),
    capacidade            INTEGER     NOT NULL CHECK (capacidade > 0),
    possui_acessibilidade BOOLEAN     NOT NULL DEFAULT FALSE,
    status                VARCHAR(30) NOT NULL DEFAULT 'DISPONIVEL'
                          CHECK (status IN ('DISPONIVEL', 'EM_VIAGEM', 'MANUTENCAO', 'INATIVO')),
    removido_em           TIMESTAMPTZ
);

-- Placa única apenas entre veículos ativos.
CREATE UNIQUE INDEX ux_veiculos_placa ON veiculos (upper(placa)) WHERE removido_em IS NULL;

-- ─────────────────────────── Escala de motoristas ──────────────────────────
CREATE TABLE escalas_motorista (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    motorista    UUID        NOT NULL REFERENCES usuarios (id),
    data         DATE        NOT NULL,
    hora_inicial TIME        NOT NULL,
    hora_final   TIME        NOT NULL,
    status       VARCHAR(30) NOT NULL DEFAULT 'AGENDADA',
    criado_por   UUID        NOT NULL REFERENCES usuarios (id),
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_escalas_motorista_data ON escalas_motorista (data, motorista);

-- ─────────────────────────────── Viagens ───────────────────────────────────
CREATE TABLE viagens (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    veiculo          UUID        NOT NULL REFERENCES veiculos (id),
    motorista        UUID        NOT NULL REFERENCES usuarios (id),
    cidade_destino   UUID        NOT NULL REFERENCES cidades (id),
    data_viagem      DATE        NOT NULL,
    horario_saida    TIME        NOT NULL,
    horario_chegada  TIME        NOT NULL,
    retorno_previsto TIMESTAMPTZ,
    status           VARCHAR(30) NOT NULL DEFAULT 'PLANEJADA'
                     CHECK (status IN ('PLANEJADA', 'CONFIRMADA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
    criado_por       UUID        NOT NULL REFERENCES usuarios (id)
);
CREATE INDEX ix_viagens_data ON viagens (data_viagem, status);

-- ─────────────────────── Solicitações de transporte ────────────────────────
-- (Domínio do passageiro; mapeamento por entidade em incremento futuro.)
CREATE TABLE solicitacoes_transporte (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    passageiro             UUID        NOT NULL REFERENCES usuarios (id),
    cidade                 UUID        NOT NULL REFERENCES cidades (id),
    local_desembarque      TEXT,
    data_consulta          DATE        NOT NULL,
    horario_consulta       TIME        NOT NULL,
    horario_limite_chegada TIME        NOT NULL,
    especialidade          VARCHAR(120),
    observacoes_clinicas   TEXT,
    necessita_acompanhante BOOLEAN     NOT NULL DEFAULT FALSE,
    status                 VARCHAR(30) NOT NULL DEFAULT 'PENDENTE'
);
CREATE INDEX ix_solicitacoes_pendentes ON solicitacoes_transporte (data_consulta, status, horario_limite_chegada);

-- ──────────────────────────── Assentos de viagem ───────────────────────────
CREATE TABLE assentos_viagem (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    viagem                 UUID        NOT NULL REFERENCES viagens (id) ON DELETE CASCADE,
    passageiro             UUID        NOT NULL REFERENCES usuarios (id),
    solicitacao_transporte UUID        NOT NULL REFERENCES solicitacoes_transporte (id),
    local_desembarque      TEXT,
    horario_consulta       TIME,
    endereco_embarque      TEXT,
    eh_acompanhante        BOOLEAN     NOT NULL DEFAULT FALSE,
    assento_vinculado      UUID REFERENCES assentos_viagem (id),
    ordem_desembarque      INTEGER,
    status                 VARCHAR(30) NOT NULL DEFAULT 'RESERVADO'
);
CREATE INDEX ix_assentos_viagem ON assentos_viagem (viagem);

-- Comentários de documentação
COMMENT ON TABLE usuarios IS 'Usuários do sistema (raiz de identidade); soft-delete via removido_em';
COMMENT ON TABLE papeis_usuario IS 'Papéis (RBAC) de cada usuário: PASSAGEIRO, MOTORISTA, GERENTE';
COMMENT ON TABLE veiculos IS 'Frota de veículos; soft-delete via removido_em';
COMMENT ON TABLE viagens IS 'Viagens planejadas (veículo + motorista + cidade de destino)';
