-- ============================================================================
--  Migração V3 — Limpeza do boilerplate e dados iniciais
-- ----------------------------------------------------------------------------
--  1) Remove a tabela "produto" do boilerplate (não faz mais parte do domínio).
--     IF EXISTS torna a operação segura mesmo que a V1 não tenha sido aplicada
--     (ex.: banco novo).
--  2) Semeia cidades de referência (origem + destinos metropolitanos), as
--     mesmas usadas no design (Patos → João Pessoa / Campina Grande / Cajazeiras).
--
--  O usuário administrador (gerente) NÃO é criado aqui: ele é semeado pela
--  aplicação (DataInitializer), que usa o mesmo BCrypt do Spring Security para
--  gerar um hash compatível — evitando depender da extensão pgcrypto no banco.
-- ============================================================================

DROP TABLE IF EXISTS produto;

-- Cidades de referência (somente se a tabela ainda estiver vazia).
INSERT INTO cidades (nome, uf, tipo)
SELECT * FROM (VALUES
    ('Patos',          'PB', 'ORIGEM'),
    ('João Pessoa',    'PB', 'METROPOLITANA'),
    ('Campina Grande', 'PB', 'METROPOLITANA'),
    ('Cajazeiras',     'PB', 'METROPOLITANA')
) AS novas(nome, uf, tipo)
WHERE NOT EXISTS (SELECT 1 FROM cidades);
