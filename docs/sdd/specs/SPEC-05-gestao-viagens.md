# SPEC-05 — Gestão de Viagens

| | |
|---|---|
| **Área** | `VIA` |
| **Papéis** | GERENTE |
| **Status geral** | 🟡 Parcial (cria, lista e exclui; **não edita**) |
| **Constituição** | Artigos II (camadas), VII (RBAC), X (HTMX) |
| **Código** | `ViagemController`, `ViagemService`, `Viagem`, `ViagemForm`, `ViagemRepository`, `templates/viagens/**` |

---

## 1. Objetivo

Permitir ao **gerente** planejar **viagens**, associando um **veículo**, um **motorista** e
uma **cidade de destino** a uma data e horários de saída/chegada, e listá-las. É o ponto onde
os cadastros (usuários, veículos, cidades) convergem na operação.

---

## 2. User stories

- **US-VIA-1** — Como gerente, quero **criar** uma viagem escolhendo veículo, motorista,
  destino, data e horários, para organizar o transporte dos pacientes.
- **US-VIA-2** — Como gerente, quero **listar** as viagens com seus dados consolidados
  (veículo, motorista, cidade), para ter visão do planejamento.
- **US-VIA-3** — Como gerente, quero **excluir** uma viagem cancelada/equivocada.

---

## 3. Requisitos funcionais

- **FR-VIA-01** ✅ — `GET /viagens` lista viagens paginadas (10/página), já com
  veículo/motorista/cidade carregados (fetch join, evitando N+1). HTMX → fragmento; normal →
  página completa.
- **FR-VIA-02** ✅ — `GET /viagens/nova` devolve o **modal** com os selects preenchidos:
  veículos ativos, motoristas (usuários com papel `MOTORISTA`) e cidades.
- **FR-VIA-03** ✅ — `POST /viagens` cria a viagem; sucesso → fragmento da linha; erro
  (validação ou regra) → modal com mensagens e selects recarregados.
- **FR-VIA-04** ✅ — O **gerente autenticado** é registrado como `criadoPor` da viagem
  (obtido de `@AuthenticationPrincipal`).
- **FR-VIA-05** ✅ — `DELETE /viagens/{id}` remove a viagem (**físico**; assentos em cascata
  no banco). `200`/`404`.
- **FR-VIA-06** ✅ — Toda viagem nasce com status **`PLANEJADA`**.
- **FR-VIA-07** ✅ — Rotas `/viagens/**` exigem papel **`GERENTE`**.
- **FR-VIA-08** ⬜ — *(Planejado)* Editar viagem e **transicionar status**
  (`PLANEJADA → CONFIRMADA → EM_ANDAMENTO → CONCLUIDA | CANCELADA`).

---

## 4. Regras de negócio

- **RN-VIA-01** — **Horário de chegada deve ser após o de saída** (`horarioChegada >
  horarioSaida`); senão rejeita com "O horário de chegada deve ser após o horário de saída".
- **RN-VIA-02** — O **veículo** referenciado deve existir e estar **ativo** (não removido).
- **RN-VIA-03** — O **motorista** referenciado deve existir, estar ativo e **possuir o papel
  `MOTORISTA`**; senão "O usuário selecionado não possui o papel de motorista".
- **RN-VIA-04** — A **cidade de destino** referenciada deve existir.
- **RN-VIA-05** — O `criadoPor` deve ser um usuário existente (o gerente logado).
- **RN-VIA-06** — Referências inexistentes resultam em `RecursoNaoEncontradoException`
  (tratada como erro no modal).

---

## 5. Validações de entrada (ViagemForm)

| Campo | Regra |
|---|---|
| `veiculoId` | obrigatório (UUID do select) |
| `motoristaId` | obrigatório (UUID do select) |
| `cidadeDestinoId` | obrigatório (UUID do select) |
| `dataViagem` | obrigatória, ISO `AAAA-MM-DD` |
| `horarioSaida` | obrigatório, ISO `HH:MM` |
| `horarioChegada` | obrigatório, ISO `HH:MM`; **> saída** (regra no serviço) |

> `retorno_previsto` existe na entidade/tabela mas **não** é capturado no formulário atual.

---

## 6. Critérios de aceite (Dado / Quando / Então)

- **CA-VIA-01 — Criar viagem válida**
  *Dado* um veículo ativo, um motorista (papel `MOTORISTA`) e uma cidade,
  *Quando* o gerente cria a viagem com chegada após a saída,
  *Então* a viagem é salva como `PLANEJADA`, com `criadoPor` = gerente logado, e aparece na
  lista.

- **CA-VIA-02 — Chegada antes da saída**
  *Dado* saída `10:00` e chegada `09:00`,
  *Quando* o gerente envia,
  *Então* recebe "O horário de chegada deve ser após o horário de saída".

- **CA-VIA-03 — Motorista sem papel**
  *Dado* um usuário que não tem o papel `MOTORISTA` selecionado como motorista,
  *Quando* o gerente envia,
  *Então* recebe "O usuário selecionado não possui o papel de motorista".

- **CA-VIA-04 — Veículo removido**
  *Dado* um `veiculoId` de um veículo já excluído (soft-delete),
  *Quando* o gerente envia,
  *Então* a criação falha com recurso não encontrado.

- **CA-VIA-05 — Lista sem N+1**
  *Dado* várias viagens,
  *Quando* o gerente abre `/viagens`,
  *Então* cada linha mostra veículo, motorista e cidade sem disparar consultas adicionais por
  linha (fetch join).

- **CA-VIA-06 — Excluir viagem**
  *Quando* o gerente exclui uma viagem existente,
  *Então* ela some da lista (`200`); um id inexistente devolve `404`.

- **CA-VIA-07 — Acesso restrito**
  *Dado* usuário sem `GERENTE`, *quando* acessa `/viagens`, *então* é negado.

---

## 7. Ciclo de vida da viagem

```
        criar
          │
          ▼
     PLANEJADA ──► CONFIRMADA ──► EM_ANDAMENTO ──► CONCLUIDA
          │             │               │
          └─────────────┴───────────────┴────► CANCELADA
```
> **Hoje só existe `PLANEJADA`** na prática: não há transição de status implementada
> (FR-VIA-08 planejado). As demais constam na enum/`CHECK` para evolução.

---

## 8. Casos de borda e lacunas conhecidas

- **Sem checagem de conflito**: o mesmo veículo ou motorista pode ser alocado em duas viagens
  sobrepostas no tempo — não há validação de disponibilidade. **Candidato a regra futura**
  (cruzar com `escalas_motorista`).
- **Data no passado**: não há bloqueio para `dataViagem` anterior a hoje.
- **Exclusão física**: viagem não tem soft-delete; ao excluir, os `assentos_viagem` caem em
  cascata (quando esse módulo existir).
- **`retorno_previsto`** não é coletado pela UI atual.
- **Status do veículo** não é atualizado para `EM_VIAGEM` ao criar a viagem.

---

## 9. Dependências e fora do escopo

- **Depende de**: SPEC-02 (motoristas), SPEC-03 (veículos ativos), SPEC-04 (cidades).
- **Fora do escopo (incrementos futuros)**:
  - **Edição** de viagem e **transição de status** (FR-VIA-08).
  - **Assentos de viagem** e **alocação automática** de passageiros por prioridade/
    horário-limite (tabelas `assentos_viagem` e `solicitacoes_transporte` já existem).
  - **Escalas de motorista** e validação de disponibilidade.
  - Visão da viagem pelo **motorista** e pelo **passageiro**.

---

## 10. Rastreabilidade

| Requisito | Artefato |
|---|---|
| FR-VIA-01..07 | `ViagemController`, `ViagemService` |
| RN-VIA-01..06 | `ViagemService.criar` |
| Modelo | `Viagem`, tabela `viagens` (V2) |
| Validação | `ViagemForm` |
| Consultas | `ViagemRepository.listarComRelacionamentos` |
| Telas | `templates/viagens/{lista,fragments/*}.html` |
| Futuro | `assentos_viagem`, `solicitacoes_transporte`, `escalas_motorista` (V2) |
