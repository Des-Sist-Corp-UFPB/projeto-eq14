-- ===========================================================================
--  Migracao V9 - Linhas programadas e evolucao das viagens (SPEC-06)
-- ---------------------------------------------------------------------------
--  - linhas_programadas: template recorrente (origem->destino, horarios, ativa).
--  - linha_dias: tabela filha (1-N) com os dias da semana em que a linha opera
--    (@ElementCollection de DiaSemana). Decisao ADR-12.
--  - viagens (ocorrencia): ganha tipo (ROTINEIRA/IMPREVISTA), FK p/ a linha,
--    origem (cidade-sede por padrao) + origem_improvisada (texto), e
--    horario_retorno (TIME). Remove retorno_previsto (Instant), realinhado p/
--    LocalTime (DT-09).
--
--  Migration aditiva e forward-only (Constituicao Art. IV).
-- ===========================================================================

CREATE TABLE linhas_programadas (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    cidade_origem   UUID    REFERENCES cidades (id),
    cidade_destino  UUID    NOT NULL REFERENCES cidades (id),
    horario_saida   TIME    NOT NULL,
    horario_chegada TIME    NOT NULL,
    horario_retorno TIME,
    ativa           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE linha_dias (
    linha UUID        NOT NULL REFERENCES linhas_programadas (id) ON DELETE CASCADE,
    dia   VARCHAR(20) NOT NULL
          CHECK (dia IN ('DOMINGO','SEGUNDA','TERCA','QUARTA','QUINTA','SEXTA','SABADO')),
    PRIMARY KEY (linha, dia)
);

COMMENT ON TABLE linhas_programadas IS 'Linhas recorrentes (template) das viagens rotineiras';
COMMENT ON TABLE linha_dias IS 'Dias da semana em que cada linha opera (ElementCollection)';

-- Evolucao da tabela de viagens (ocorrencia)
ALTER TABLE viagens ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'IMPREVISTA'
      CHECK (tipo IN ('ROTINEIRA', 'IMPREVISTA'));
ALTER TABLE viagens ADD COLUMN linha_programada  UUID REFERENCES linhas_programadas (id);
ALTER TABLE viagens ADD COLUMN cidade_origem     UUID REFERENCES cidades (id);
ALTER TABLE viagens ADD COLUMN origem_improvisada VARCHAR(180);
ALTER TABLE viagens ADD COLUMN horario_retorno   TIME;

-- DT-09: substitui retorno_previsto (TIMESTAMPTZ) por horario_retorno (TIME).
ALTER TABLE viagens DROP COLUMN retorno_previsto;
