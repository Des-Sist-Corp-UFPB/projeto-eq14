-- V15 — Feature toggle (SPEC-13 / ADR-17)
-- Evolução ADITIVA (Art. IV): não altera migrations existentes, sem extensões nem
-- superusuário (Art. XIV). Única mudança de schema da feature: o *entitlement* de
-- pagamento por município (RN-FLG-05).
--
-- As flags globais (feature.*) e os parâmetros de negócio (param.*) NÃO precisam de
-- schema: são linhas chave/valor na tabela configuracoes_sistema (V5), lidas pelo
-- FeatureFlagService com default no código (RN-FLG-02, D1).

-- Adesão do município ao (futuro) fluxo de pagamento — SPEC-13 D3.
-- Default false: nenhum município entra no fluxo sem decisão explícita.
ALTER TABLE municipios
    ADD COLUMN pagamento_habilitado BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN municipios.pagamento_habilitado IS
    'Entitlement (SPEC-13 RN-FLG-05): município aderiu ao fluxo de pagamento.';
