# SPEC-07 — Perfil e Endereço do Passageiro

| | |
|---|---|
| **Área** | `END` (endereços) / `PAS` (perfil do passageiro) |
| **Papéis** | PASSAGEIRO (próprio), GERENTE (consulta/análise) |
| **Status geral** | ✅ Implementado — migration **V8** (`municipios` + `enderecos`), cadastro/perfil + aba de análise |
| **Constituição** | Artigos II (camadas), IV (migrations), VI (UUID/enums), X (HTMX) |
| **Relacionada** | `perfis_passageiro` (V2), [SPEC-01](SPEC-01-autenticacao.md), [SPEC-04](SPEC-04-gestao-cidades.md) |
| **Código (a criar)** | `Endereco` (entidade), `enderecos` (migration), coleta no cadastro, edição de perfil |

---

## 1. Objetivo

Dar ao passageiro um **endereço estruturado** (cidade, bairro, logradouro, número, CEP) em vez
do `endereco JSONB` não-estruturado herdado da `perfis_passageiro` (V2). Estruturar permite
**filtros e análises** ("quantos passageiros do bairro Centro?", "demanda por cidade de origem")
e prepara o endereço de **embarque** usado no transporte.

> **Motivação (do dono do projeto):** *"criar uma tabela de endereços, onde vai ter campo de
> ruas/avenidas, bairros, etc. Para poder fazer filtros e análises com os passageiros."*

---

## 2. Decisão de modelagem (✅ aprovada na revisão)

- **(1) Tabela `enderecos` dedicada e estruturada** (não JSONB) — colunas próprias permitem
  índice e agregação por bairro/município.
- **(3) Município como FK para uma nova tabela de referência `municipios`** — **não** para
  `cidades`. Motivo: `cidades` modela cidades de **viagem** (origem/destinos); moradia é outro
  conceito. `municipios` é uma **lista pré-definida** (o passageiro **seleciona**, não digita),
  evitando erros de digitação e padronizando a análise. **Seed: apenas Paraíba (~223 municípios)**
  por enquanto (estende-se para outros estados no futuro).
- **(4) Campos:** `logradouro` (rua/av.), `numero`, `complemento`, `bairro`, `cep` e
  **`ponto_referencia`** (texto livre — útil no interior, ex.: "próximo à igreja matriz").
- **(7) Relação 1-para-1** passageiro→endereço nesta etapa (UNIQUE em `usuario`); evolui para
  1-para-N (com `tipo`) no futuro.
- **(2) `perfis_passageiro.endereco` (JSONB)** é **descartado**: a migration **remove a coluna**
  (sem entidade/uso). Os demais campos de `perfis_passageiro` (data_nascimento, condicao_saude,
  mobilidade_reduzida, cadeirante) ganham a entidade `PerfilPassageiro`.

### 2.1 Esquema previsto

```
municipios            (tabela de REFERÊNCIA, semeada na migration — só PB por enquanto)
  id          UUID PK (gen_random_uuid)
  nome        VARCHAR(120) NOT NULL
  uf          CHAR(2)      NOT NULL          -- "PB"
  codigo_ibge VARCHAR(7)                     -- opcional, para integrações/análise
  UNIQUE (nome, uf)

enderecos
  id              UUID PK (gen_random_uuid)
  usuario         UUID NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE
  municipio       UUID REFERENCES municipios(id)     -- selecionado de lista (análise)
  bairro          VARCHAR(120)                        -- texto livre (eixo principal de análise)
  logradouro      VARCHAR(180)
  numero          VARCHAR(20)
  complemento     VARCHAR(120)
  ponto_referencia VARCHAR(180)                       -- "próximo à praça central"
  cep             VARCHAR(9)                           -- "58700-000"
  atualizado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
  índices: (municipio), (bairro)
```

> A unicidade de `usuario` garante 1 endereço por passageiro nesta etapa. As 4 cidades de viagem
> (Patos/JP/Campina/Cajazeiras) também existem em `municipios` — sem conflito, pois `cidades`
> (viagem) e `municipios` (moradia) têm propósitos distintos.

---

## 3. User stories

- **US-END-1** — Como passageiro, quero **informar meu endereço** (cidade, bairro, rua, número,
  CEP) no cadastro ou no meu perfil, para que o transporte saiba meu ponto de embarque.
- **US-END-2** — Como passageiro, quero **editar** meu endereço quando mudar.
- **US-PAS-1** — Como gerente, quero uma **aba de análise** que conte passageiros por **bairro**
  e **município**, para planejar a demanda.

---

## 4. Requisitos funcionais (planejados)

- **FR-END-01** ⬜ — O **cadastro de passageiro** passa a coletar o endereço (**município** de um
  select pré-definido, bairro, logradouro, número, complemento, ponto de referência, CEP) —
  campos **opcionais** para não travar o auto-cadastro.
- **FR-END-02** ⬜ — Página **"Meu perfil"** (passageiro autenticado) para ver/editar endereço e
  dados de perfil (mobilidade reduzida, cadeirante, etc.).
- **FR-END-03** ⬜ — `Municipio` (entidade + seed PB), `Endereco` mapeia `enderecos`,
  `PerfilPassageiro` mapeia `perfis_passageiro` (sem o JSONB).
- **FR-END-04** ⬜ — **Aba "Análise" (GERENTE)** — tela simples com a **contagem de passageiros
  por bairro** (e por município). Item próprio no menu.
- **FR-END-05** ⬜ — CEP normalizado (dígitos + máscara `00000-000`); o município vem da lista.

---

## 5. Regras de negócio

- **RN-END-01** — Endereço é **opcional** no auto-cadastro (passageiro completa depois no perfil).
- **RN-END-02** — **Sem endereço (completo) não há solicitação de viagem** — o passageiro só pode
  pedir transporte tendo endereço cadastrado. *(A solicitação de viagem é incremento futuro —
  `solicitacoes_transporte`; esta regra fica registrada e será aplicada quando aquele fluxo
  existir; já exponho um `enderecoCadastrado(usuarioId)` para uso futuro.)*
- **RN-END-03** — `municipio` referencia um município da lista (FK); bairro/logradouro `trim`ados.
- **RN-END-04** — Um passageiro tem **no máximo um** endereço nesta etapa (UNIQUE em `usuario`).
- **RN-END-05** — Excluir o usuário remove o endereço (cascade).

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-END-01** — *Dado* um passageiro sem endereço, *quando* seleciona município=Patos,
  bairro=Jatobá e salva, *então* há um registro em `enderecos` ligado a ele.
- **CA-END-02** — *Dado* um endereço existente, *quando* o passageiro edita o bairro, *então* o
  mesmo registro é atualizado (não cria outro).
- **CA-END-03** — *Dado* vários passageiros, *quando* o gerente abre a aba de análise, *então* vê
  a contagem agregada por bairro.
- **CA-END-04** — *Dado* CEP `58700000`, *quando* salvo, *então* é normalizado para `58700-000`.
- **CA-END-05** — *Dado* um passageiro com endereço, *quando* a conta é excluída, *então* o
  endereço some (cascade).
- **CA-END-06** — *Dado* o seed da migration, *então* a lista `municipios` contém os municípios
  da Paraíba (incl. Patos, João Pessoa, Campina Grande, Cajazeiras).

---

## 7. Fora do escopo (futuro)

- Múltiplos endereços por passageiro (`tipo` RESIDENCIAL/EMBARQUE).
- Geocodificação / integração com API de CEP (autocompletar).
- Painel/relatório analítico rico (gráficos) — esta spec entrega a **consulta** base.
- Uso do endereço de embarque na **alocação** de viagens (depende de `solicitacoes_transporte`).

---

## 8. Sequenciamento

Esta spec é implementada **antes** da SPEC-06 (viagens rotineiras): a migration de `enderecos`
ocupa o **V8**; a de linhas/viagens passa a ser **V9**. Decisão do dono do projeto:
*"Trate B agora e abra a spec de endereço do passageiro antes do V8."*

---

## 9. Rastreabilidade (planejada)

| Requisito | Artefato (a criar) |
|---|---|
| FR-END-01..02 | tela de cadastro/perfil do passageiro |
| FR-END-03 | `Endereco`, `PerfilPassageiro`, `enderecos` (V8), alteração de `perfis_passageiro` |
| FR-END-04 | `EnderecoRepository` (consultas de contagem por bairro/cidade) |
| Modelo | `enderecos` (V8), `cidades` (FK) |
