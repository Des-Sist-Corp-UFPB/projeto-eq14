# SPEC-04 — Gestão de Cidades

| | |
|---|---|
| **Área** | `CID` |
| **Papéis** | GERENTE |
| **Status geral** | ✅ Implementado |
| **Constituição** | Artigos II (camadas), III (exceção: remoção física), X (HTMX) |
| **Código** | `CidadeController`, `CidadeService`, `Cidade`, `CidadeForm`, `CidadeRepository`, `templates/cidades/**` |

---

## 1. Objetivo

Manter o cadastro de **cidades** — dados de referência que definem a **origem** (município de
partida dos pacientes) e os **destinos metropolitanos** (onde ficam as consultas). São
usadas como destino das viagens.

---

## 2. User stories

- **US-CID-1** — Como gerente, quero cadastrar cidades de **origem** e **metropolitanas**,
  para usá-las como destino ao planejar viagens.
- **US-CID-2** — Como gerente, quero editar ou remover cidades cadastradas incorretamente.
- **US-CID-3** — Como gerente, quero buscar cidades por nome/UF em uma lista paginada.

---

## 3. Requisitos funcionais

- **FR-CID-01** ✅ — `GET /cidades` lista cidades paginadas (10/página). HTMX → fragmento;
  normal → página completa.
- **FR-CID-02** ✅ — Busca por texto sobre nome/UF; busca vazia retorna todas.
- **FR-CID-03** ✅ — `GET /cidades/nova` → modal em branco; `GET /cidades/{id}/editar` →
  modal preenchido.
- **FR-CID-04** ✅ — `POST /cidades` cria; `PUT /cidades/{id}` atualiza (sucesso → linha,
  erro → modal).
- **FR-CID-05** ✅ — `DELETE /cidades/{id}` **remove fisicamente** a cidade.
- **FR-CID-06** ✅ — O formulário oferece as opções de **tipo** (`TipoCidade`:
  `ORIGEM`/`METROPOLITANA`).
- **FR-CID-07** ✅ — `CidadeService.listarTodas()` retorna todas as cidades ordenadas por
  nome, para alimentar o select de viagens (ver SPEC-05).
- **FR-CID-08** ✅ — Todas as rotas `/cidades/**` exigem papel **`GERENTE`**.
- **FR-CID-09** ✅ — Cidades de referência são **semeadas** pela migration `V3` (Patos /
  João Pessoa / Campina Grande / Cajazeiras), apenas se a tabela estiver vazia.

---

## 4. Regras de negócio

- **RN-CID-01** — **UF normalizada**: `trim` + **maiúsculas**; deve ter exatamente 2 letras.
- **RN-CID-02** — Nome é `trim`ado.
- **RN-CID-03** — **Tipo obrigatório**: `ORIGEM` ou `METROPOLITANA` (`CHECK` no banco).
- **RN-CID-04** — **Remoção é física** (diferente de usuários/veículos): cidades são dados de
  referência, sem soft-delete. ⚠️ Ver §7 (risco de integridade).
- **RN-CID-05** — Não há unicidade obrigatória de nome+UF (é possível cadastrar duplicatas).

---

## 5. Validações de entrada (CidadeForm)

| Campo | Regra |
|---|---|
| `nome` | obrigatório, ≤ 120 |
| `uf` | obrigatória, exatamente 2 letras (ex.: `PB`) |
| `tipo` | obrigatório (`ORIGEM`/`METROPOLITANA`) |

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-CID-01 — UF normalizada**
  *Dado* a UF `pb`, *quando* a cidade é salva, *então* é armazenada como `PB`.

- **CA-CID-02 — Tipo obrigatório**
  *Dado* um formulário sem tipo, *quando* enviado, *então* é rejeitado ("O tipo é
  obrigatório").

- **CA-CID-03 — Cidade aparece em viagens**
  *Dado* uma cidade metropolitana cadastrada,
  *Quando* o gerente abre o formulário de nova viagem,
  *Então* a cidade aparece no select de destino (ordenada por nome).

- **CA-CID-04 — Seed inicial**
  *Dado* um banco recém-criado,
  *Quando* as migrations rodam,
  *Então* existem Patos (origem) e João Pessoa/Campina Grande/Cajazeiras (metropolitanas).

- **CA-CID-05 — Acesso restrito**
  *Dado* usuário sem `GERENTE`, *quando* acessa `/cidades`, *então* é negado.

---

## 7. Casos de borda e riscos

- **⚠️ Excluir cidade usada por viagem** → a remoção física pode violar a FK
  `viagens.cidade_destino → cidades(id)`, gerando erro de integridade no banco. Não há
  proteção em serviço hoje. **Candidato a regra futura** (bloquear exclusão de cidade
  referenciada, ou migrar para soft-delete).
- **UF com mais/menos de 2 letras** → rejeitada pela validação.
- **Duplicatas de nome+UF** → permitidas (sem unicidade).

---

## 8. Fora do escopo

- Validação de UF contra a lista oficial de unidades federativas.
- Unicidade de cidade (nome+UF+tipo).
- Vínculo origem↔destinos (quais metropolitanas atendem qual origem).
- Soft-delete de cidade.

---

## 9. Rastreabilidade

| Requisito | Artefato |
|---|---|
| FR-CID-01..08 | `CidadeController`, `CidadeService` |
| FR-CID-09 | `V3__remover_produto_e_seed_cidades.sql` |
| Modelo | `Cidade`, tabela `cidades` (V2) |
| Validação | `CidadeForm` |
| Consultas | `CidadeRepository` |
| Telas | `templates/cidades/{lista,fragments/*}.html` |
