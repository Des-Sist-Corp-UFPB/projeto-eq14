# SPEC-06 — Viagens Rotineiras e Imprevistas

| | |
|---|---|
| **Área** | `VIA` (evolução) / `LIN` (linhas programadas) |
| **Papéis** | GERENTE |
| **Status geral** | ✅ Implementado — migration **V9** (linhas + linha_dias + viagens), painel semanal, designação, conflito, status, visão do motorista |
| **Constituição** | Artigos II (camadas), IV (migrations), VI (UUID/enums), VII (RBAC), X (HTMX) |
| **Relacionada** | [SPEC-05](SPEC-05-gestao-viagens.md) (viagens atuais), [SPEC-03](SPEC-03-gestao-veiculos.md), [SPEC-04](SPEC-04-gestao-cidades.md) |
| **Código (a criar)** | `LinhaProgramada`, `Viagem` (alterada), `linhas_programadas` (V_), painel semanal |

---

## 1. Objetivo

Modelar dois tipos de viagem, inspirados na operação real de linhas de ônibus
(ex.: sempre há uma saída às 08:00 de uma cidade para a metropolitana):

- **Viagem rotineira** — deriva de uma **linha programada** (template recorrente por dia da
  semana) que "sempre existe" até o gerente desabilitá-la. A cada dia, o gerente só **designa
  veículo e motorista** para cada linha do dia.
- **Viagem imprevista** — avulsa, criada pontualmente (ex.: levar pacientes a um hospital
  especializado), sem recorrência.

A tela central é um **painel semanal (domingo a sábado)** onde as linhas do dia ficam
predispostas e o gerente preenche veículo/motorista.

> **Decisão de modelagem (padrão template × ocorrência):** a `LinhaProgramada` é o *plano*; a
> `Viagem` é a *ocorrência datada*. Toda viagem rotineira referencia a linha que a originou; a
> imprevista tem `linha = null`. Materialização **preguiçosa** (a `Viagem` nasce quando o
> gerente designa veículo/motorista no dia) — evita pré-gerar viagens futuras indefinidamente.

---

## 2. Decisões do produto (do dono do projeto)

1. **Cidade sede (origem).** A origem é a **cidade-sede** onde a secretaria de saúde está
   situada — um valor **configurável pelo gerente** (uma única cidade sede do município). As
   linhas partem da sede para a cidade de destino. Ver SPEC de Configuração do Sistema (sede
   como parâmetro) e [Constituição](../00-constituicao.md).
2. **Conflito de disponibilidade (DT-03).** Ao designar motorista/veículo a uma ocorrência,
   checar se já estão alocados em **outra ocorrência sobreposta no mesmo dia/horário** →
   indisponíveis, salvo se desligados da viagem. "Carga horária" do motorista evolui com a
   tabela `escalas_motorista`.
3. **Ida e volta (DT-09).** A viagem carrega o **horário previsto de retorno** (saída da
   cidade metropolitana de volta à sede) como informação da própria viagem. Alinhar o tipo
   para `LocalTime` (hoje `retorno_previsto` é `Instant`, inconsistente).
4. **Data no passado (DT-08).** Só se aplica a **imprevistas** (têm data fixa); rotineiras são
   por dia-da-semana. Imprevista com data anterior a hoje é rejeitada.

### 2.1 Decisões confirmadas na revisão (✅ aprovadas pelo dono do projeto)

| # | Tema | Decisão aprovada |
|---|---|---|
| 1 | Dias da semana | **Tabela filha `linha_dias`** (abordagem A) — 1 registro por dia (`DOMINGO..SABADO`), relação 1-para-muitos com `linhas_programadas`. **Não** é coluna nem atributo da `viagem`. |
| 2 | Materialização | **Sob demanda (lazy)** — a `Viagem` nasce quando o gerente designa veículo/motorista no painel da semana. |
| 3 | Origem | **Editável (B)** — default = cidade-sede (configurável em `/admin/configuracoes`); na imprevista o gerente pode informar um **local improvisado** (texto livre, não precisa ser endereço estruturado). |
| 4 | Retorno (DT-09) | **`horario_retorno` como `LocalTime`** (substitui o `retorno_previsto` `Instant`). |
| 5 | Conflito (DT-03) | **Versão 1**: bloquear apenas **sobreposição de horário** no mesmo dia. Carga horária (escalas) fica para evolução futura. |
| 6 | Status (DT-05) | **Ciclo completo (B)**: `PLANEJADA→CONFIRMADA→EM_ANDAMENTO→CONCLUIDA|CANCELADA`. **Motorista e gerente** alteram o status; **passageiro** apenas **visualiza** a viagem em que foi alocado (visão do passageiro depende da alocação — incremento futuro). |
| 7 | Painel semanal | Grade com os **dias no topo** (Domingo→Sábado em colunas) e as viagens de cada dia de forma **compacta** abaixo de cada coluna. |

> **Endereço do passageiro:** a ideia de tabela de endereços estruturada (para filtros/análise)
> foi separada na [SPEC-07](SPEC-07-endereco-do-passageiro.md) e será implementada **antes** desta
> (a migration de `enderecos` ocupa o **V8**; a de linhas/viagens passa a **V9**).

---

## 3. Modelo de dados (a criar)

### 3.1 `LinhaProgramada` (template)

| Campo | Tipo | Observação |
|---|---|---|
| `id` | UUID | `gen_random_uuid()` |
| `cidade_origem` | FK → `cidades` | default = cidade sede (configurável) |
| `cidade_destino` | FK → `cidades` | metropolitana |
| `horario_saida` | TIME | |
| `horario_chegada` | TIME | > saída |
| `horario_retorno` | TIME (nullable) | saída da volta (DT-09) |
| `dias_semana` | tabela filha `linha_dias` | dias em que a linha opera (ver nota) |
| `ativa` | BOOLEAN | gerente desabilita sem apagar |

> **✅ Decisão aprovada (abordagem A):** `dias_semana` é a **tabela filha `linha_dias`**
> — uma linha por dia (`DOMINGO..SABADO`), relação 1-para-muitos com `linhas_programadas`
> (em JPA: `@ElementCollection` de um enum `DiaSemana`, no mesmo padrão de `papeis_usuario`).
> **Não** é coluna/bitmask e **não** fica na tabela `viagens`. Permite consultas limpas
> (`WHERE dia = 'TERCA'`).

### 3.2 `Viagem` (ocorrência — alteração da entidade atual)

| Campo novo/alterado | Tipo | Observação |
|---|---|---|
| `linha_programada` | FK → `linhas_programadas` (nullable) | null = **imprevista** |
| `tipo` | VARCHAR + CHECK (`ROTINEIRA`/`IMPREVISTA`) | enum `TipoViagem` |
| `cidade_origem` | FK → `cidades` (nullable) | default = cidade-sede |
| `origem_improvisada` | VARCHAR (nullable) | local de partida em texto livre p/ imprevistas (item 3) |
| `horario_retorno` | TIME (nullable) | substitui/realinha `retorno_previsto` (DT-09) |

> Migration **V9** (após o **V8** de `enderecos`, SPEC-07): cria `linhas_programadas` (+
> `linha_dias`) e **altera** `viagens` (novas colunas + FK + `tipo` default `IMPREVISTA` para as
> linhas existentes). Forward-only e aditiva (Constituição Art. IV + [Plano §2.5](../02-plano-tecnico.md)).

---

## 4. User stories

- **US-VIA-4** — Como gerente, quero **cadastrar linhas programadas** (origem sede → destino,
  horários, dias da semana), para refletir as viagens que se repetem.
- **US-VIA-5** — Como gerente, quero um **painel semanal (dom–sáb)** com as linhas de cada dia,
  para **designar veículo e motorista** rapidamente.
- **US-VIA-6** — Como gerente, quero **desabilitar** uma linha sem perder o histórico.
- **US-VIA-7** — Como gerente, quero **criar uma viagem imprevista** com data específica.
- **US-VIA-8** — Como gerente, quero ser **impedido de alocar** um motorista/veículo já ocupado
  em horário sobreposto.

---

## 5. Requisitos funcionais (planejados)

- **FR-LIN-01** ⬜ — CRUD de `LinhaProgramada` (`/linhas`), papel GERENTE, padrão HTMX.
- **FR-LIN-02** ⬜ — Linha tem origem (default sede), destino, horários e **dias da semana**.
- **FR-LIN-03** ⬜ — Desabilitar/reabilitar linha (`ativa`) sem exclusão física.
- **FR-VIA-09** ⬜ — **Painel semanal** (`/viagens/semana`) lista, por dia, as linhas ativas
  daquele dia-da-semana + o estado de designação (com/sem motorista/veículo).
- **FR-VIA-10** ⬜ — **Designar** veículo+motorista a uma linha num dia → materializa a `Viagem`
  rotineira (`tipo=ROTINEIRA`, `linha` preenchida).
- **FR-VIA-11** ⬜ — **Desligar** motorista/veículo de uma ocorrência (libera disponibilidade).
- **FR-VIA-12** ⬜ — Criar **viagem imprevista** (`tipo=IMPREVISTA`, `linha=null`, data fixa).
- **FR-VIA-13** ⬜ — Coletar `horario_retorno` (ida e volta) no formulário.
- **FR-CFG-01** ⬜ — Cidade **sede** configurável (default de origem) — ver SPEC de Configuração.

---

## 6. Regras de negócio (planejadas)

- **RN-VIA-07** — `horario_chegada > horario_saida` (mantém RN-VIA-01); se houver retorno,
  `horario_retorno > horario_chegada`.
- **RN-VIA-08 (DT-03)** — Um **motorista** não pode estar em duas ocorrências com **janelas de
  horário sobrepostas** no mesmo dia; idem **veículo**. Designação que conflite é rejeitada com
  mensagem clara. (Evolui com `escalas_motorista` para carga horária.)
- **RN-VIA-09 (DT-08)** — Viagem **imprevista** com `data` anterior a hoje é rejeitada.
- **RN-VIA-10** — Linha **desabilitada** não aparece no painel semanal nem gera novas
  ocorrências; ocorrências já materializadas permanecem.
- **RN-VIA-11** — Origem das viagens assume a **cidade sede** quando não informada.
- **RN-LIN-01** — `dias_semana` não vazio; cada dia ∈ {DOMINGO..SABADO}.

---

## 7. Critérios de aceite (Dado / Quando / Então)

- **CA-VIA-08 — Linha aparece no dia certo**
  *Dado* uma linha ativa nas terças e quintas, *quando* o gerente abre o painel semanal,
  *então* ela aparece **apenas** sob terça e quinta.

- **CA-VIA-09 — Designar materializa a viagem rotineira**
  *Dado* uma linha de terça sem motorista, *quando* o gerente designa um motorista (papel
  MOTORISTA) e um veículo DISPONÍVEL, *então* é criada uma `Viagem` `ROTINEIRA` vinculada à
  linha, com a sede como origem.

- **CA-VIA-10 — Conflito de motorista (DT-03)**
  *Dado* um motorista já alocado às 08:00–10:00 de terça, *quando* o gerente tenta alocá-lo em
  outra linha sobreposta na mesma terça, *então* a designação é **rejeitada** ("motorista
  indisponível neste horário").

- **CA-VIA-11 — Desligar libera disponibilidade**
  *Dado* um motorista alocado, *quando* o gerente o desliga da ocorrência, *então* ele volta a
  ficar disponível para outra linha no mesmo dia.

- **CA-VIA-12 — Imprevista no passado (DT-08)**
  *Dado* uma viagem imprevista com data de ontem, *quando* o gerente envia, *então* é rejeitada.

- **CA-VIA-13 — Linha desabilitada some do painel**
  *Dado* uma linha desabilitada, *quando* o gerente abre o painel, *então* ela não aparece, mas
  as viagens já materializadas dela continuam na listagem.

- **CA-VIA-14 — Acesso restrito** — usuário sem GERENTE não acessa `/linhas` nem `/viagens/**`.

---

## 8. Impacto na SPEC-05

A SPEC-05 (criar/listar/excluir viagem) continua válida para **imprevistas** e ganha o eixo
**rotineira** via designação no painel. O `retorno_previsto` (Instant) é **realinhado** para
`horario_retorno` (LocalTime). A listagem passa a exibir o **tipo** (rotineira/imprevista).

---

## 9. Fora do escopo (futuro)

- **Assentos e alocação automática** de passageiros por prioridade (tabelas `assentos_viagem`,
  `solicitacoes_transporte`) — incrementos B/C do [roadmap](../03-tarefas-e-roadmap.md).
- **Escalas de motorista** completas (carga horária semanal) — `escalas_motorista`.
- Geração antecipada (eager) de ocorrências para semanas futuras.
- Visão da viagem pelo **motorista**/**passageiro**.

---

## 10. Rastreabilidade (planejada)

| Requisito | Artefato (a criar) |
|---|---|
| FR-LIN-01..03 | `LinhaProgramada`, `LinhaController`, `LinhaService`, `linhas_programadas` (V_) |
| FR-VIA-09..13 | `ViagemController`/`ViagemService` (painel semanal + designação) |
| RN-VIA-08 (DT-03) | `ViagemService.designar` (checagem de conflito) |
| FR-CFG-01 | `ConfiguracaoSistema` (cidade sede) — SPEC de Configuração do Sistema |
| Modelo | `Viagem` (tipo, linha, origem, horario_retorno) |
