# SPEC-03 — Gestão de Veículos

| | |
|---|---|
| **Área** | `VEI` |
| **Papéis** | GERENTE |
| **Status geral** | ✅ Implementado |
| **Constituição** | Artigos II (camadas), III (soft-delete), X (HTMX) |
| **Código** | `VeiculoController`, `VeiculoService`, `Veiculo`, `VeiculoForm`, `VeiculoRepository`, `templates/veiculos/**` |

---

## 1. Objetivo

Permitir ao **gerente** administrar a **frota**: cadastrar, listar/buscar, editar e excluir
(logicamente) veículos, registrando capacidade e acessibilidade — insumos do planejamento de
viagens.

---

## 2. User stories

- **US-VEI-1** — Como gerente, quero cadastrar veículos com placa, tipo e capacidade, para
  ter a frota disponível ao planejar viagens.
- **US-VEI-2** — Como gerente, quero marcar se o veículo **possui acessibilidade**, para
  atender pacientes com mobilidade reduzida (regra de alocação futura).
- **US-VEI-3** — Como gerente, quero atualizar o **status** do veículo (disponível, em
  viagem, manutenção, inativo), para refletir a operação.
- **US-VEI-4** — Como gerente, quero excluir veículos sem perder o histórico das viagens em
  que foram usados.

---

## 3. Requisitos funcionais

- **FR-VEI-01** ✅ — `GET /veiculos` lista veículos **ativos** paginados (10/página). HTMX →
  fragmento da tabela; normal → página completa.
- **FR-VEI-02** ✅ — Busca por texto (`busca`) sobre veículos ativos; busca vazia retorna
  todos.
- **FR-VEI-03** ✅ — `GET /veiculos/novo` → modal em branco; `GET /veiculos/{id}/editar` →
  modal preenchido.
- **FR-VEI-04** ✅ — `POST /veiculos` cria; sucesso → fragmento da linha; erro → modal com
  mensagens.
- **FR-VEI-05** ✅ — `PUT /veiculos/{id}` atualiza (mesma semântica de resposta).
- **FR-VEI-06** ✅ — `DELETE /veiculos/{id}` faz **soft-delete** (`200`; `404` se inexistente).
- **FR-VEI-07** ✅ — O formulário oferece as opções de **tipo** (`TipoVeiculo`) e **status**
  (`StatusVeiculo`).
- **FR-VEI-08** ✅ — `VeiculoService.listarAtivos()` expõe os veículos ativos ordenados por
  placa, para alimentar o select de viagens (ver SPEC-05).
- **FR-VEI-09** ✅ — Todas as rotas `/veiculos/**` exigem papel **`GERENTE`**.

---

## 4. Regras de negócio

- **RN-VEI-01** — **Placa normalizada**: `trim`, **maiúsculas**, sem hífen (`ABC-1D23` →
  `ABC1D23`).
- **RN-VEI-02** — **Placa única** entre veículos ativos. Na criação rejeita duplicata; na
  edição rejeita se outra ativa (id diferente) já usa a placa.
- **RN-VEI-03** — **Status padrão `DISPONIVEL`** na criação (definido no construtor/`@PrePersist`);
  respeita um status informado, se houver.
- **RN-VEI-04** — Na edição, status ausente recai para `DISPONIVEL`.
- **RN-VEI-05** — **Exclusão lógica** (soft-delete): preenche `removido_em`; placa liberada
  para reuso entre ativos.
- **RN-VEI-06** — `marca` e `modelo` são `trim`ados.

---

## 5. Validações de entrada (VeiculoForm)

| Campo | Regra |
|---|---|
| `placa` | obrigatória, 7–8 caracteres (ex.: `ABC1D23`) |
| `marca` | obrigatória, ≤ 60 |
| `modelo` | obrigatório, ≤ 60 |
| `ano` | obrigatório, 1950–2100 |
| `tipo` | obrigatório (`CARRO`/`VAN`/`MICRO_ONIBUS`/`ONIBUS`/`AMBULANCIA`) |
| `capacidade` | obrigatória, 1–100 |
| `possuiAcessibilidade` | booleano (default `false`) |
| `status` | usado só na edição |

> As mesmas restrições estão também anotadas na entidade `Veiculo` (defesa em profundidade) e
> refletidas no `CHECK` da tabela (`capacidade > 0`, tipos/status válidos).

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-VEI-01 — Placa normalizada e única**
  *Dado* que não há veículo ativo com placa `ABC1D23`,
  *Quando* o gerente cadastra `abc-1d23`,
  *Então* é salvo como `ABC1D23` e uma segunda tentativa com a mesma placa é rejeitada.

- **CA-VEI-02 — Status default na criação**
  *Dado* um cadastro sem status informado,
  *Quando* o veículo é criado,
  *Então* nasce `DISPONIVEL`.

- **CA-VEI-03 — Capacidade inválida**
  *Dado* capacidade `0`,
  *Quando* o gerente salva,
  *Então* a validação rejeita ("A capacidade deve ser ao menos 1").

- **CA-VEI-04 — Edição troca placa para uma já usada**
  *Dado* dois veículos ativos A (placa P1) e B (placa P2),
  *Quando* o gerente edita B para a placa P1,
  *Então* recebe "Já existe outro veículo ativo com a placa P1".

- **CA-VEI-05 — Soft-delete libera placa**
  *Dado* um veículo ativo com placa P,
  *Quando* o gerente o exclui e cadastra outro com placa P,
  *Então* o novo é aceito.

- **CA-VEI-06 — Veículo disponível aparece em viagens**
  *Dado* um veículo ativo,
  *Quando* o gerente abre o formulário de nova viagem,
  *Então* o veículo aparece no select (ordenado por placa).

- **CA-VEI-07 — Acesso restrito**
  *Dado* usuário sem `GERENTE`, *quando* acessa `/veiculos`, *então* é negado.

---

## 7. Casos de borda

- **Placa com hífen ou minúsculas** → normalizada antes da checagem de unicidade.
- **Status `EM_VIAGEM`/`MANUTENCAO`** → veículo continua aparecendo em `listarAtivos()` (o
  filtro é por `removido_em`, não por status). Ver §8.
- **Ano fora de 1950–2100** → rejeitado pela validação.

---

## 8. Lacunas conhecidas / fora do escopo

- `listarAtivos()` não filtra por `status == DISPONIVEL`; um veículo em `MANUTENCAO` ainda
  aparece como selecionável em viagens. Possível regra futura (alocação só de disponíveis).
- Não há sincronização automática de status (ex.: marcar `EM_VIAGEM` ao vincular a uma
  viagem) — é manual.
- Sem checagem de conflito de uso do mesmo veículo em viagens sobrepostas (ver SPEC-05, §8).

---

## 9. Rastreabilidade

| Requisito | Artefato |
|---|---|
| FR-VEI-01..09 | `VeiculoController` |
| RN-VEI-01..06 | `VeiculoService` |
| Modelo | `Veiculo`, tabela `veiculos` (V2) |
| Validação | `VeiculoForm` + anotações na entidade |
| Consultas | `VeiculoRepository` |
| Telas | `templates/veiculos/{lista,fragments/*}.html` |
| Testes | `VeiculoServiceTest` |
